-- OBD Health: server schema for PostgreSQL
-- Apply with: psql -U obd_admin -d obd -f obd_schema.sql

CREATE EXTENSION IF NOT EXISTS citext;

-- ---------- Types ----------
CREATE TYPE fuel_type        AS ENUM ('PETROL', 'DIESEL', 'HYBRID', 'ELECTRIC', 'LPG');
CREATE TYPE trip_exit_reason AS ENUM (
    'USER_STOPPED',          -- user tapped "Finish"
    'ENGINE_OFF',            -- RPM is 0 and the ECU stopped responding
    'ADAPTER_DISCONNECTED',  -- Bluetooth connection dropped
    'ECU_TIMEOUT',           -- adapter is connected but the car does not answer
    'APP_INTERRUPTED'        -- app was closed by the system
);
CREATE TYPE assessment_mode  AS ENUM ('REFERENCE', 'BLENDED');   -- reference norm only / reference + own baseline
CREATE TYPE severity_level   AS ENUM ('INFO', 'WARNING', 'CRITICAL');
CREATE TYPE detector_kind    AS ENUM ('RULE', 'ML');
CREATE TYPE urgency_level    AS ENUM ('LOW', 'MEDIUM', 'HIGH');

-- ---------- Users ----------
CREATE TABLE users (
    id             BIGSERIAL PRIMARY KEY,
    email          CITEXT       NOT NULL UNIQUE,
    password_hash  TEXT         NOT NULL,
    locale         VARCHAR(5)   NOT NULL DEFAULT 'uk',      -- uk / en
    theme          VARCHAR(6)   NOT NULL DEFAULT 'system',  -- light / dark / system
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- ---------- Reference (theoretical) norms by vehicle class ----------
CREATE TABLE reference_profiles (
    id               SERIAL PRIMARY KEY,
    name             TEXT         NOT NULL,
    fuel             fuel_type    NOT NULL,
    engine_vol_min   NUMERIC(3,1),
    engine_vol_max   NUMERIC(3,1),
    year_from        SMALLINT,
    year_to          SMALLINT
);

CREATE TABLE reference_norms (
    id            SERIAL PRIMARY KEY,
    profile_id    INT          NOT NULL REFERENCES reference_profiles(id) ON DELETE CASCADE,
    metric        VARCHAR(32)  NOT NULL,   -- coolant_temp_c, ltft_pct, battery_v, ...
    state         VARCHAR(16)  NOT NULL,   -- idle / driving
    min_value     NUMERIC(8,2),
    max_value     NUMERIC(8,2),
    source        TEXT         NOT NULL,   -- link or name of the source
    UNIQUE (profile_id, metric, state)
);

-- ---------- Vehicles ----------
CREATE TABLE vehicles (
    id                   BIGSERIAL PRIMARY KEY,
    user_id              BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    vin                  VARCHAR(17),
    make                 TEXT         NOT NULL,
    model                TEXT         NOT NULL,
    year                 SMALLINT     NOT NULL,
    fuel                 fuel_type    NOT NULL,
    engine_vol_l         NUMERIC(3,1),
    odometer_base_km     INT          NOT NULL DEFAULT 0,   -- last value checked against the real odometer
    odometer_base_date   DATE         NOT NULL DEFAULT CURRENT_DATE,
    reference_profile_id INT          REFERENCES reference_profiles(id),
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (user_id, vin)
);
CREATE INDEX idx_vehicles_user ON vehicles(user_id);

-- ---------- Trips ----------
CREATE TABLE trips (
    id                   BIGSERIAL PRIMARY KEY,
    client_trip_id       UUID         NOT NULL UNIQUE,   -- id generated on the phone, makes repeated sync idempotent
    vehicle_id           BIGINT       NOT NULL REFERENCES vehicles(id) ON DELETE CASCADE,
    started_at           TIMESTAMPTZ  NOT NULL,
    ended_at             TIMESTAMPTZ,
    exit_reason          trip_exit_reason,
    distance_km          NUMERIC(8,2) NOT NULL DEFAULT 0, -- calculated from speed, approximate
    readings_count       INT          NOT NULL DEFAULT 0,
    counts_for_baseline  BOOLEAN      NOT NULL DEFAULT FALSE,
    mode                 assessment_mode NOT NULL DEFAULT 'REFERENCE',
    health_index         SMALLINT     CHECK (health_index BETWEEN 0 AND 100),
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_trips_vehicle_time ON trips(vehicle_id, started_at DESC);

-- ---------- Sensor readings ----------
CREATE TABLE readings (
    id               BIGSERIAL PRIMARY KEY,
    trip_id          BIGINT       NOT NULL REFERENCES trips(id) ON DELETE CASCADE,
    ts               TIMESTAMPTZ  NOT NULL,
    rpm              INT,
    speed_kmh        SMALLINT,
    coolant_temp_c   SMALLINT,
    engine_load_pct  NUMERIC(5,2),
    throttle_pct     NUMERIC(5,2),
    maf_gs           NUMERIC(7,2),
    stft_pct         NUMERIC(5,2),
    ltft_pct         NUMERIC(5,2),
    battery_v        NUMERIC(4,1),
    intake_temp_c    SMALLINT
);
CREATE INDEX idx_readings_trip_ts ON readings(trip_id, ts);

-- ---------- Trip event log ----------
CREATE TABLE trip_events (
    id          BIGSERIAL PRIMARY KEY,
    trip_id     BIGINT         NOT NULL REFERENCES trips(id) ON DELETE CASCADE,
    ts          TIMESTAMPTZ    NOT NULL,
    event_type  VARCHAR(32)    NOT NULL,   -- CONNECTION_LOST, RECONNECTED, ENGINE_OFF, ...
    severity    severity_level NOT NULL DEFAULT 'INFO',
    message     TEXT
);
CREATE INDEX idx_trip_events_trip ON trip_events(trip_id, ts);

-- ---------- Anomalies ----------
CREATE TABLE anomalies (
    id           BIGSERIAL PRIMARY KEY,
    trip_id      BIGINT         NOT NULL REFERENCES trips(id) ON DELETE CASCADE,
    ts_from      TIMESTAMPTZ    NOT NULL,
    ts_to        TIMESTAMPTZ    NOT NULL,
    metric       VARCHAR(32)    NOT NULL,
    detector     detector_kind  NOT NULL,   -- rule-based or ML
    severity     severity_level NOT NULL,
    score        NUMERIC(6,3),              -- anomaly score for ML detections
    description  TEXT
);
CREATE INDEX idx_anomalies_trip ON anomalies(trip_id);

-- ---------- Diagnostic trouble codes ----------
CREATE TABLE dtc_dictionary (
    code            VARCHAR(5) PRIMARY KEY,   -- e.g. P0171
    description_uk  TEXT NOT NULL,
    description_en  TEXT NOT NULL
);

CREATE TABLE dtc_events (
    id          BIGSERIAL PRIMARY KEY,
    vehicle_id  BIGINT      NOT NULL REFERENCES vehicles(id) ON DELETE CASCADE,
    trip_id     BIGINT      REFERENCES trips(id) ON DELETE SET NULL,
    code        VARCHAR(5)  NOT NULL REFERENCES dtc_dictionary(code),
    read_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    cleared_at  TIMESTAMPTZ
);
CREATE INDEX idx_dtc_vehicle ON dtc_events(vehicle_id, read_at DESC);

-- ---------- Per-vehicle baseline (own "normal") ----------
CREATE TABLE baseline_profiles (
    vehicle_id  BIGINT PRIMARY KEY REFERENCES vehicles(id) ON DELETE CASCADE,
    trips_used  SMALLINT    NOT NULL DEFAULT 0,
    stats       JSONB       NOT NULL DEFAULT '{}',   -- means and spreads per feature
    model_path  TEXT,                                 -- path to the trained model file
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ---------- Forecasts ----------
CREATE TABLE forecasts (
    id          BIGSERIAL PRIMARY KEY,
    vehicle_id  BIGINT        NOT NULL REFERENCES vehicles(id) ON DELETE CASCADE,
    created_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    trend       JSONB         NOT NULL,   -- trend calculated by the server
    ai_text     TEXT          NOT NULL,   -- explanation written by the AI model
    urgency     urgency_level NOT NULL
);
CREATE INDEX idx_forecasts_vehicle ON forecasts(vehicle_id, created_at DESC);

-- ---------- Maintenance log ----------
CREATE TABLE maintenance_log (
    id            BIGSERIAL PRIMARY KEY,
    vehicle_id    BIGINT       NOT NULL REFERENCES vehicles(id) ON DELETE CASCADE,
    performed_on  DATE         NOT NULL,
    odometer_km   INT,
    kind          VARCHAR(32)  NOT NULL,   -- oil, filter, brakes, ...
    note          TEXT
);

-- ---------- Notifications ----------
CREATE TABLE notifications (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    vehicle_id  BIGINT       REFERENCES vehicles(id) ON DELETE CASCADE,
    trip_id     BIGINT       REFERENCES trips(id) ON DELETE SET NULL,
    type        VARCHAR(32)  NOT NULL,   -- ANOMALY, HEALTH_DROP, SERVICE_DUE
    title       TEXT         NOT NULL,
    body        TEXT,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    read_at     TIMESTAMPTZ
);
CREATE INDEX idx_notifications_user ON notifications(user_id, created_at DESC);