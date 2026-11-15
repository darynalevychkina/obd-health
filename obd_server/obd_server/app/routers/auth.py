from typing import Annotated

from fastapi import APIRouter, Depends, HTTPException, status
from fastapi.security import OAuth2PasswordRequestForm
from sqlalchemy import select

from ..deps import CurrentUser, DbSession
from ..models import User
from ..schemas import RegisterIn, SettingsIn, TokenOut, UserOut
from ..security import create_access_token, hash_password, verify_password

router = APIRouter(prefix="/auth", tags=["auth"])


@router.post("/register", response_model=UserOut, status_code=status.HTTP_201_CREATED)
def register(data: RegisterIn, db: DbSession):
    if db.scalar(select(User).where(User.email == data.email)):
        raise HTTPException(status.HTTP_409_CONFLICT, "Користувач з таким email уже існує")
    user = User(email=data.email, password_hash=hash_password(data.password))
    db.add(user)
    db.commit()
    db.refresh(user)
    return user


@router.post("/login", response_model=TokenOut)
def login(form: Annotated[OAuth2PasswordRequestForm, Depends()], db: DbSession):
    # У полі username передаємо email (так вимагає стандарт OAuth2)
    user = db.scalar(select(User).where(User.email == form.username))
    if user is None or not verify_password(form.password, user.password_hash):
        raise HTTPException(status.HTTP_401_UNAUTHORIZED, "Невірний email або пароль",
                            headers={"WWW-Authenticate": "Bearer"})
    return TokenOut(access_token=create_access_token(user.id))


@router.get("/me", response_model=UserOut)
def me(user: CurrentUser):
    return user


@router.put("/me/settings", response_model=UserOut)
def update_settings(data: SettingsIn, user: CurrentUser, db: DbSession):
    if data.locale is not None:
        user.locale = data.locale
    if data.theme is not None:
        user.theme = data.theme
    db.commit()
    db.refresh(user)
    return user
