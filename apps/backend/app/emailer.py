import smtplib, ssl
from email.message import EmailMessage
from app.config import settings

def _send_email(to: str, subject: str, html: str):
    msg = EmailMessage()
    msg["Subject"] = subject
    msg["From"] = settings.SMTP_FROM
    msg["To"] = to
    msg.set_content("This email requires HTML.")
    msg.add_alternative(html, subtype="html")
    context = ssl.create_default_context()
    with smtplib.SMTP_SSL(settings.SMTP_HOST, settings.SMTP_PORT, context=context) as s:
        s.login(settings.SMTP_USERNAME, settings.SMTP_PASSWORD)
        s.send_message(msg)

def send_verification_email(email: str, token: str):
    link = f"{settings.BASE_URL}/auth/verify?token={token}"
    html = f"""
    <h2>Подтвердите свой Note2Tex аккаунт</h2>
    <p>Перейдите по ссылке, чтобы верефицировать:</p>
    <p><a href="{link}">Ссылка</a></p>
    <p>Проигнорируйте письмо, если вы не запрашивали его</p>
    """
    _send_email(email, "Note2Tex: подтверждение почты", html)

def send_reset_email(email: str, token: str):
    link = f"{settings.BASE_URL}/auth/reset/confirm?token={token}"
    html = f"""
    <h2>Сбросьте свой пароль</h2>
    <p>Перейдите по ссылке:</p>
    <p><a href="{link}">Ссылка</a></p>
    """
    _send_email(email, "Note2Tex: сброс пароля", html)
