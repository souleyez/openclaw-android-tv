import type { Metadata } from "next";
import type { ReactNode } from "react";
import "./globals.css";

export const metadata: Metadata = {
  title: "Sonance 管理后台",
  description: "用于模型池、广播目录、计费和运营管理的后台。",
};

export default function RootLayout({
  children
}: Readonly<{
  children: ReactNode;
}>) {
  return (
    <html lang="zh-CN">
      <body>{children}</body>
    </html>
  );
}
