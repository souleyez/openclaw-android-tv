'use client';

import { useEffect, useState } from 'react';
import { useRouter, useSearchParams } from 'next/navigation';

async function requestJson(url, init = {}) {
  const response = await fetch(url, {
    ...init,
    headers: {
      ...(init.body !== undefined ? { 'Content-Type': 'application/json' } : {}),
      ...(init.headers || {}),
    },
  });
  const payload = await response.json().catch(() => ({}));
  if (!response.ok) {
    throw new Error(payload.code || payload.error || response.statusText);
  }
  return payload;
}

function describeLoginError(code) {
  if (code === 'PLATFORM_KEY_REQUIRED') {
    return '请输入密钥';
  }
  if (code === 'PLATFORM_KEY_INVALID') {
    return '密钥错误';
  }
  if (code === 'ADMIN_SESSION_INVALID' || code === 'ADMIN_SESSION_EXPIRED') {
    return '登录已失效';
  }
  return code;
}

export default function LoginPage() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const [platformKey, setPlatformKey] = useState('');
  const [notice, setNotice] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const [checkingSession, setCheckingSession] = useState(true);
  const requestedNextPath = searchParams.get('next') || '';
  const nextPath = requestedNextPath.startsWith('/') && !requestedNextPath.startsWith('//')
    ? requestedNextPath
    : '/';

  useEffect(() => {
    let active = true;

    requestJson('/api/admin/session')
      .then((payload) => {
        if (!active) {
          return;
        }
        if (payload.authenticated) {
          router.replace(nextPath);
          router.refresh();
          return;
        }
        setCheckingSession(false);
      })
      .catch(() => {
        if (active) {
          setCheckingSession(false);
        }
      });

    return () => {
      active = false;
    };
  }, [nextPath, router]);

  async function handlePlatformLogin(event) {
    event.preventDefault();
    if (!platformKey.trim()) {
      setError('PLATFORM_KEY_REQUIRED');
      setNotice('');
      return;
    }
    setLoading(true);
    try {
      setError('');
      const payload = await requestJson('/api/admin/session', {
        method: 'POST',
        body: JSON.stringify({ platformKey }),
      });
      setNotice(payload.claimedNow ? '已认领' : '已进入');
      router.replace(nextPath);
      router.refresh();
    } catch (nextError) {
      setError(nextError instanceof Error ? nextError.message : String(nextError));
      setNotice('');
    } finally {
      setLoading(false);
    }
  }

  return (
    <main className="cp-cloak-shell cp-login-shell">
      <section className="cp-cloak-panel cp-login-panel">
        <div className="cp-login-copy">
          <span className="cp-cloak-kicker">Access</span>
          <h1>输入密钥</h1>
        </div>

        {notice ? <div className="cp-banner success">{notice}</div> : null}
        {error ? <div className="cp-banner error">{describeLoginError(error)}</div> : null}

        <form className="cp-login-form" onSubmit={handlePlatformLogin}>
          <label className="cp-login-field">
            <input
              type="password"
              value={platformKey}
              onChange={(event) => setPlatformKey(event.target.value)}
              placeholder="平台密钥"
              autoFocus
            />
          </label>
          <button className="cp-primary-btn" type="submit" disabled={loading || checkingSession}>
            {checkingSession ? '检查中...' : (loading ? '校验中...' : '继续')}
          </button>
        </form>
      </section>
    </main>
  );
}
