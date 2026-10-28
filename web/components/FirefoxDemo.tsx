'use client';
import Image from 'next/image';
import { useEffect, useState } from 'react';
import logo from '@/assets/logo.svg';

// Makieta: rozszerzenie wpisuje login, hasło i kod 2FA tak, jak robi to na prawdziwej stronie.
const STEPS = [
  { user: '', pass: '', otp: false, done: false, msg: 'Otwieram github.com/login…' },
  { user: 'annanowak-dev', pass: '', otp: false, done: false, msg: 'Wpisuję login…' },
  { user: 'annanowak-dev', pass: '••••••••••••••', otp: false, done: false, msg: 'Wpisuję hasło…' },
  { user: '', pass: '', otp: true, done: false, msg: 'Wpisuję kod 2FA z Ostoi…' },
  { user: '', pass: '', otp: true, done: true, msg: 'Zalogowano do GitHub z kodem 2FA' },
];

export function FirefoxDemo() {
  const [i, setI] = useState(0);
  useEffect(() => {
    const id = setInterval(() => setI((x) => (x + 1) % STEPS.length), 1700);
    return () => clearInterval(id);
  }, []);
  const s = STEPS[i];
  return (
    <div className="card browser">
      <div className="browser-bar">
        <div className="dots"><i /><i /><i /></div>
        <div className="url">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" aria-hidden><rect x="5" y="11" width="14" height="10" rx="2" /><path d="M8 11V8a4 4 0 0 1 8 0v3" /></svg>
          {s.otp ? 'github.com/sessions/two-factor/app' : 'github.com/login'}
        </div>
        <Image className="ext-btn" src={logo} alt="Ostoja" width={30} height={30} />
      </div>
      <div className="browser-body">
        {s.otp ? (
          <div className="gh-form">
            <h4>Two-factor authentication</h4>
            <div className="gh-box">
              <label>Authentication code</label>
              <div className={`gh-input${s.done ? '' : ' on'}`}>{s.done ? '' : '482 913'}</div>
              <div className="gh-btn">{s.done ? '✓' : 'Verify'}</div>
            </div>
          </div>
        ) : (
          <div className="gh-form">
            <h4>Sign in to GitHub</h4>
            <div className="gh-box">
              <label>Username or email address</label>
              <div className={`gh-input${s.user && !s.pass ? ' on' : ''}`}>{s.user}</div>
              <label>Password</label>
              <div className={`gh-input${s.pass ? ' on' : ''}`}>{s.pass}</div>
              <div className="gh-btn">Sign in</div>
            </div>
          </div>
        )}
        <div className="ext-pill">
          <Image src={logo} alt="" width={24} height={24} />
          <span className={s.done ? 'ok' : ''}>{s.msg}</span>
        </div>
      </div>
    </div>
  );
}
