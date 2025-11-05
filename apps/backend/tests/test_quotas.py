import re
import types
from datetime import datetime, timedelta, timezone
import pytest

from app import quotas
from app.config import settings

def test_month_key_now_format():
    mk = quotas.month_key_now()
    assert re.fullmatch(r"\d{4}-\d{2}", mk)

def test_is_premium_true_and_false(monkeypatch):
    from app.models import Plan

    now = datetime.now(timezone.utc)
    user_prem_ok = types.SimpleNamespace(plan=Plan.premium, plan_expires_at=now + timedelta(days=1))
    user_prem_expired = types.SimpleNamespace(plan=Plan.premium, plan_expires_at=now - timedelta(days=1))
    user_free = types.SimpleNamespace(plan=Plan.free, plan_expires_at=None)

    assert quotas.is_premium(user_prem_ok) is True
    assert quotas.is_premium(user_prem_expired) is False
    assert quotas.is_premium(user_free) is False

@pytest.mark.asyncio
async def test_can_consume_and_consume(monkeypatch):
    monkeypatch.setattr(settings, "FREE_PAGES_PER_MONTH", 3, raising=False)

    class FakeUsage:
        def __init__(self): self.pages_used = 0

    class FakeSession:
        async def flush(self): pass

    usage = FakeUsage()
    session = FakeSession()

    async def fake_get_or_create_usage(session_, user_id):
        return usage

    monkeypatch.setattr(quotas, "get_or_create_usage", fake_get_or_create_usage)

    user = types.SimpleNamespace(id=1, plan=getattr(__import__("app.models").models, "Plan").free, plan_expires_at=None)

    assert await quotas.can_consume(session, user, pages=1) is True
    await quotas.consume(session, user, pages=1)
    assert usage.pages_used == 1

    assert await quotas.can_consume(session, user, pages=2) is True
    await quotas.consume(session, user, pages=2)
    assert usage.pages_used == 3

    assert await quotas.can_consume(session, user, pages=1) is False

@pytest.mark.asyncio
async def test_can_consume_premium_bypasses_limit(monkeypatch):
    monkeypatch.setattr(settings, "FREE_PAGES_PER_MONTH", 1, raising=False)

    class FakeSession:
        async def flush(self): pass

    session = FakeSession()

    from app.models import Plan
    user = types.SimpleNamespace(id=42, plan=Plan.premium, plan_expires_at=datetime.now(timezone.utc) + timedelta(days=30))
    assert await quotas.can_consume(session, user, pages=1000) is True
