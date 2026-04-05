import type { Metadata } from "next";
import type { ReactNode } from "react";
import "./globals.css";

export const metadata: Metadata = {
  title: "OpenClaw Legacy Admin",
  description: "Deprecated Android TV admin surface kept only as a migration notice.",
};

export default function RootLayout({
  children: _children,
}: Readonly<{
  children: ReactNode;
}>) {
  return (
    <html lang="zh-CN">
      <body>
        <main className="legacy-shell">
          <section className="legacy-card">
            <p className="legacy-kicker">Legacy Surface</p>
            <h1>Android TV admin has moved to home</h1>
            <p className="legacy-copy">
              This workspace is frozen. Shared admin, shared model pool, and cross-project management now belong to the
              public platform repository.
            </p>
            <div className="legacy-links">
              <a href="http://127.0.0.1:3003/login">Open local home admin</a>
              <a href="https://ad.goods-editor.com/login">Open deployed home admin</a>
            </div>
            <p className="legacy-copy" style={{ marginTop: "18px" }}>
              Boundary doc: <code>docs/APP_BOUNDARY_2026-04-05.md</code>
            </p>
          </section>
        </main>
      </body>
    </html>
  );
}
