'use client';
import { useEffect, useState } from 'react';

// Prawdziwe kody TOTP (RFC 6238) liczone w przeglądarce - ten sam algorytm co shared/data/Otp.kt.
const ACCOUNTS = [
  { name: 'Google', user: 'anna.nowak@gmail.com', color: '#4285f4', secret: 'JBSWY3DPEHPK3PXP' },
  { name: 'GitHub', user: 'annanowak-dev', color: '#2b3137', secret: 'KRSXG5CTMVRXEZLU' },
  { name: 'Discord', user: 'anna_nowak', color: '#5865f2', secret: 'MFRGGZDFMZTWQ2LK' },
  { name: 'Allegro', user: 'anna.nowak', color: '#ff5a00', secret: 'GEZDGNBVGY3TQOJQ' },
];

function base32(s: string) {
  const A = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ234567';
  let bits = '';
  for (const ch of s) bits += A.indexOf(ch).toString(2).padStart(5, '0');
  const out = new Uint8Array(Math.floor(bits.length / 8));
  for (let i = 0; i < out.length; i++) out[i] = parseInt(bits.slice(i * 8, i * 8 + 8), 2);
  return out;
}

async function totp(secret: string, counter: number) {
  const key = await crypto.subtle.importKey('raw', base32(secret), { name: 'HMAC', hash: 'SHA-1' }, false, ['sign']);
  const msg = new ArrayBuffer(8);
  new DataView(msg).setUint32(4, counter);
  const h = new Uint8Array(await crypto.subtle.sign('HMAC', key, msg));
  const o = h[19] & 15;
  const bin = ((h[o] & 127) << 24) | (h[o + 1] << 16) | (h[o + 2] << 8) | h[o + 3];
  return String(bin % 1_000_000).padStart(6, '0');
}

export function TotpDemo() {
  const [now, setNow] = useState<number | null>(null);
  const [codes, setCodes] = useState<string[]>(ACCOUNTS.map(() => '••• •••'));

  useEffect(() => {
    let last = -1;
    const tick = async () => {
      const t = Date.now();
      setNow(t);
      const c = Math.floor(t / 30000);
      if (c !== last && window.crypto?.subtle) {
        last = c;
        const list = await Promise.all(ACCOUNTS.map((a) => totp(a.secret, c)));
        setCodes(list.map((x) => `${x.slice(0, 3)} ${x.slice(3)}`));
      }
    };
    tick();
    const id = setInterval(tick, 1000);
    return () => clearInterval(id);
  }, []);

  const left = now == null ? 30 : 30 - Math.floor((now / 1000) % 30);
  const warn = left <= 5;
  const r = 10;
  const len = 2 * Math.PI * r;

  return (
    <div className="card codes">
      <div className="codes-head">
        <span>Kody 2FA · {ACCOUNTS.length} konta</span>
        <span>Nowe kody za {left} s</span>
      </div>
      {ACCOUNTS.map((a, i) => (
        <div className="code-row" key={a.name}>
          <div className="avatar" style={{ background: a.color }}>{a.name[0]}</div>
          <div className="code-name"><b>{a.name}</b><span>{a.user}</span></div>
          <div className={`code-val${warn ? ' warn' : ''}`}>{codes[i]}</div>
          <svg className={`ring${warn ? ' warn' : ''}`} viewBox="0 0 26 26" aria-hidden>
            <circle className="bg" cx="13" cy="13" r={r} />
            <circle className="fg" cx="13" cy="13" r={r} strokeDasharray={len} strokeDashoffset={len * (1 - left / 30)} />
          </svg>
        </div>
      ))}
    </div>
  );
}
