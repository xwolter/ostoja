// Ikony liniowe w stylu aplikacji (desktop/Icons.kt): obrys 2 px, zaokrąglone końce.
const base = { fill: 'none', stroke: 'currentColor', strokeWidth: 2, strokeLinecap: 'round', strokeLinejoin: 'round' } as const;

function icon(d: React.ReactNode) {
  const C = () => <svg viewBox="0 0 24 24" aria-hidden {...base}>{d}</svg>;
  return C;
}

export const Ic = {
  Lock: icon(<><rect x="4" y="11" width="16" height="10" rx="3" /><path d="M8 11V8a4 4 0 0 1 8 0v3" /></>),
  Timer: icon(<><circle cx="12" cy="13" r="8" /><path d="M12 9v4l2.5 2M9 2h6" /></>),
  ShieldCheck: icon(<><path d="M12 3l8 3v6c0 4.5-3.4 8.3-8 9-4.6-.7-8-4.5-8-9V6l8-3z" /><path d="M8.5 12l2.5 2.5 4.5-5" /></>),
  Devices: icon(<><rect x="2" y="4" width="15" height="11" rx="2" /><path d="M6 19h7" /><rect x="17" y="9" width="5" height="11" rx="1.5" /></>),
  Phone: icon(<><rect x="6" y="2" width="12" height="20" rx="3" /><path d="M11 18h2" /></>),
  Windows: icon(<path d="M3 5.5l7.5-1v7H3zM12.5 4.2L21 3v8.5h-8.5zM3 12.5h7.5v7L3 18.5zM12.5 12.5H21V21l-8.5-1.2z" />),
  Firefox: icon(<><circle cx="12" cy="12" r="9" /><path d="M7 7c1 2 3 2.5 5 2.5 3 0 4.5 2 4.5 4a4.5 4.5 0 0 1-8.7 1.6" /></>),
  Download: icon(<><path d="M12 3v12m0 0l-5-5m5 5l5-5" /><path d="M4 17v2a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-2" /></>),
  Github: icon(<path d="M9 19c-4.3 1.4-4.3-2.5-6-3m12 5v-3.5c0-1 .1-1.4-.5-2 2.8-.3 5.5-1.4 5.5-6a4.6 4.6 0 0 0-1.3-3.2 4.2 4.2 0 0 0-.1-3.2s-1.1-.3-3.5 1.3a12.3 12.3 0 0 0-6.2 0C6.5 2.8 5.4 3.1 5.4 3.1a4.2 4.2 0 0 0-.1 3.2A4.6 4.6 0 0 0 4 9.5c0 4.6 2.7 5.7 5.5 6-.6.6-.6 1.2-.5 2V21" />),
  Check: icon(<path d="M5 12.5l4.5 4.5L19 7.5" />),
  Key: icon(<><circle cx="8" cy="15" r="4" /><path d="M11 12l9-9m-3 3l3 3m-6 0l2 2" /></>),
  Wifi: icon(<><path d="M2 8.5a15 15 0 0 1 20 0M5 12a10 10 0 0 1 14 0M8.5 15.5a5 5 0 0 1 7 0" /><circle cx="12" cy="19" r="1" /></>),
  Fingerprint: icon(<path d="M12 11v3a8 8 0 0 1-1.5 4.5M8 13.5V11a4 4 0 0 1 8 0v1.5M16 16a14 14 0 0 1-1 4M5 16.5c.6-1.6 1-3.5 1-5.5a6 6 0 0 1 9-5.2M19 10a7 7 0 0 1 .5 3.5" />),
  Heart: icon(<path d="M12 20s-7-4.4-7-10a4 4 0 0 1 7-2.6A4 4 0 0 1 19 10c0 5.6-7 10-7 10z" />),
  Scan: icon(<><path d="M4 8V6a2 2 0 0 1 2-2h2M16 4h2a2 2 0 0 1 2 2v2M20 16v2a2 2 0 0 1-2 2h-2M8 20H6a2 2 0 0 1-2-2v-2" /><path d="M7 12h10" /></>),
  Code: icon(<path d="M8 7l-5 5 5 5M16 7l5 5-5 5M14 4l-4 16" />),
  CloudOff: icon(<><path d="M7 18a5 5 0 1 1 .9-9.9A6 6 0 0 1 19 10a4 4 0 0 1-1 8z" /><path d="M4 4l16 16" /></>),
  Grid: icon(<><rect x="4" y="4" width="7" height="7" rx="2" /><rect x="13" y="4" width="7" height="7" rx="2" /><rect x="4" y="13" width="7" height="7" rx="2" /><rect x="13" y="13" width="7" height="7" rx="2" /></>),
};
