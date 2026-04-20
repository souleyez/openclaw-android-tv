import './globals.css';

export const metadata = {
  title: 'Codex Web',
  description: 'Standalone Codex mobile control surface.',
};

/** @type {import('next').Viewport} */
export const viewport = {
  width: 'device-width',
  initialScale: 1,
  viewportFit: 'cover',
  interactiveWidget: 'resizes-content',
  themeColor: '#070909',
  colorScheme: 'dark',
};

export const dynamic = 'force-dynamic';
export const revalidate = 0;

export default function RootLayout({ children }) {
  return (
    <html lang="zh-CN">
      <body>{children}</body>
    </html>
  );
}
