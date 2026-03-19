"use server";

import { revalidatePath } from "next/cache";
import { getAdminApiBaseUrl } from "@/lib/admin-api";

async function postAdminApiPoolAccount(payload: Record<string, string>) {
  const response = await fetch(`${getAdminApiBaseUrl()}/admin/api-pool-accounts`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json"
    },
    body: JSON.stringify(payload)
  });

  if (!response.ok) {
    throw new Error(`Admin api-pool write failed with ${response.status}`);
  }
}

async function postAdminApiPoolImport(payload: Record<string, string>) {
  const response = await fetch(`${getAdminApiBaseUrl()}/admin/api-pool-accounts/import`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json"
    },
    body: JSON.stringify(payload)
  });

  if (!response.ok) {
    throw new Error(`Admin api-pool import failed with ${response.status}`);
  }
}

export async function createApiPoolAccountAction(formData: FormData) {
  await postAdminApiPoolAccount({
    provider: String(formData.get("provider") ?? "").trim(),
    accountLabel: String(formData.get("accountLabel") ?? "").trim(),
    planLabel: String(formData.get("planLabel") ?? "").trim(),
    status: String(formData.get("status") ?? "active").trim(),
    renewsAt: String(formData.get("renewsAt") ?? "").trim(),
    expiresAt: String(formData.get("expiresAt") ?? "").trim(),
    notes: String(formData.get("notes") ?? "").trim()
  });

  revalidatePath("/api-pool");
  revalidatePath("/overview");
  revalidatePath("/finance");
  revalidatePath("/risk");
}

export async function updateApiPoolAccountAction(formData: FormData) {
  await postAdminApiPoolAccount({
    id: String(formData.get("id") ?? "").trim(),
    provider: String(formData.get("provider") ?? "").trim(),
    accountLabel: String(formData.get("accountLabel") ?? "").trim(),
    planLabel: String(formData.get("planLabel") ?? "").trim(),
    status: String(formData.get("status") ?? "active").trim(),
    renewsAt: String(formData.get("renewsAt") ?? "").trim(),
    expiresAt: String(formData.get("expiresAt") ?? "").trim(),
    notes: String(formData.get("notes") ?? "").trim()
  });

  revalidatePath("/api-pool");
  revalidatePath("/overview");
  revalidatePath("/finance");
  revalidatePath("/risk");
}

export async function importApiPoolCredentialsAction(formData: FormData) {
  await postAdminApiPoolImport({
    accountId: String(formData.get("accountId") ?? "").trim(),
    provider: String(formData.get("provider") ?? "").trim(),
    baseUrl: String(formData.get("baseUrl") ?? "").trim(),
    model: String(formData.get("model") ?? "").trim(),
    rawKeys: String(formData.get("rawKeys") ?? "").trim()
  });

  revalidatePath("/api-pool");
  revalidatePath("/overview");
  revalidatePath("/finance");
  revalidatePath("/risk");
}
