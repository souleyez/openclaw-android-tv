"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { ReactNode } from "react";
import { logoutAdminAction } from "@/app/login/actions";
import { adminNavItems } from "@/lib/admin-ui";

type ShellProps = {
  title: string;
  eyebrow: string;
  children: ReactNode;
};

export function Shell({ title, eyebrow, children }: ShellProps) {
  const pathname = usePathname();

  return (
    <div className="shell">
      <aside className="sidebar">
        <div className="brand">
          <div className="brand__badge">SN</div>
          <div>
            <p className="brand__eyebrow">Sonance 运营台</p>
            <h1 className="brand__title">管理后台</h1>
          </div>
        </div>

        <nav className="nav">
          {adminNavItems.map((item) => (
            <Link
              key={item.href}
              className={`nav__item${pathname === item.href ? " nav__item--active" : ""}`}
              href={item.href}
            >
              {item.label}
            </Link>
          ))}
        </nav>

        <div className="sidebar__footer">
          <p>广播与模型池运营</p>
          <span>面向 Web / Android / iOS 客户端</span>
          <form action={logoutAdminAction}>
            <button className="sidebar__logout" type="submit">
              退出登录
            </button>
          </form>
        </div>
      </aside>

      <main className="content">
        <header className="page-header">
          <div>
            <p className="page-header__eyebrow">{eyebrow}</p>
            <h2 className="page-header__title">{title}</h2>
          </div>
          <div className="page-header__status">
            <span className="status-pill status-pill--ok">API 3000</span>
            <span className="status-pill">后台 3001</span>
          </div>
        </header>
        {children}
      </main>
    </div>
  );
}
