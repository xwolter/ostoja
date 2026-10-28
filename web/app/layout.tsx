import type { Metadata, Viewport } from 'next';
import './globals.css';

export const metadata: Metadata = {
  title: 'Ostoja - hasła i kody 2FA bez chmury',
  description: 'Menedżer haseł i kodów 2FA na Androida i Windows z rozszerzeniem do Firefoksa. Synchronizacja w domowej sieci Wi‑Fi, bez konta i bez chmury. Otwarty kod.',
  authors: [{ name: 'xwolter' }],
  openGraph: {
    title: 'Ostoja - hasła i kody 2FA bez chmury',
    description: 'Telefon, komputer i Firefox w jednym sejfie. Bez konta, bez chmury, z otwartym kodem.',
    type: 'website',
  },
};

export const viewport: Viewport = { themeColor: '#0e0f13', colorScheme: 'dark' };

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="pl">
      <head>
        <link rel="preconnect" href="https://fonts.googleapis.com" />
        <link rel="preconnect" href="https://fonts.gstatic.com" crossOrigin="" />
        <link href="https://fonts.googleapis.com/css2?family=Roboto:wght@400;500;600;700&family=Roboto+Mono:wght@400;500&display=swap" rel="stylesheet" />
      </head>
      <body>
        <div className="glow" />
        {children}
      </body>
    </html>
  );
}
