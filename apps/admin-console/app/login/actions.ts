"use server";

import { cookies } from "next/headers";
import { redirect } from "next/navigation";
import { getAdminApiBaseUrl } from "@/lib/admin-api";
import { ADMIN_SESSION_COOKIE, ADMIN_SESSION_MAX_AGE_SECONDS } from "@/lib/admin-auth";

export async function requestAdminCodeAction(formData: FormData) {
  const email = String(formData.get("email") ?? "").trim().toLowerCase();
  const response = await fetch(`${getAdminApiBaseUrl()}/auth/admin/request-code`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json"
    },
    body: JSON.stringify({ email })
  });

  if (!response.ok) {
    throw new Error(`管理员验证码发送失败：${response.status}`);
  }

  redirect(`/login?email=${encodeURIComponent(email)}&sent=1`);
}

export async function verifyAdminCodeAction(formData: FormData) {
  const email = String(formData.get("email") ?? "").trim().toLowerCase();
  const code = String(formData.get("code") ?? "").trim();
  const response = await fetch(`${getAdminApiBaseUrl()}/auth/admin/verify-code`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json"
    },
    body: JSON.stringify({ email, code })
  });

  if (!response.ok) {
    throw new Error(`管理员验证码校验失败：${response.status}`);
  }

  const payload = (await response.json()) as { accessToken: string };
  const cookieStore = await cookies();
  cookieStore.set(ADMIN_SESSION_COOKIE, payload.accessToken, {
    httpOnly: true,
    sameSite: "lax",
    secure: false,
    path: "/",
    maxAge: ADMIN_SESSION_MAX_AGE_SECONDS,
    expires: new Date(Date.now() + ADMIN_SESSION_MAX_AGE_SECONDS * 1000)
  });

  redirect("/overview");
}

export async function logoutAdminAction() {
  const cookieStore = await cookies();
  cookieStore.delete(ADMIN_SESSION_COOKIE);
  redirect("/login");
}
