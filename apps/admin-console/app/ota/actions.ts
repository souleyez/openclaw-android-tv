"use server";

import { revalidatePath } from "next/cache";
import { getAdminApiBaseUrl } from "@/lib/admin-api";

async function postAdminOta(path: string, payload: Record<string, string>) {
  const response = await fetch(`${getAdminApiBaseUrl()}${path}`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json"
    },
    body: JSON.stringify(payload)
  });

  if (!response.ok) {
    throw new Error(`Admin ota write failed with ${response.status}`);
  }
}

export async function createOtaReleaseAction(formData: FormData) {
  await postAdminOta("/admin/ota/releases", {
    versionName: String(formData.get("versionName") ?? "").trim(),
    versionCode: String(formData.get("versionCode") ?? "").trim(),
    releaseChannel: String(formData.get("releaseChannel") ?? "beta").trim(),
    rolloutStatus: String(formData.get("rolloutStatus") ?? "draft").trim(),
    targetScope: String(formData.get("targetScope") ?? "").trim(),
    rolloutPercent: String(formData.get("rolloutPercent") ?? "0").trim(),
    deviceCount: String(formData.get("deviceCount") ?? "0").trim(),
    installSuccessRate: String(formData.get("installSuccessRate") ?? "100").trim(),
    artifactUrl: String(formData.get("artifactUrl") ?? "").trim(),
    releaseNotes: String(formData.get("releaseNotes") ?? "").trim()
  });

  revalidatePath("/ota");
  revalidatePath("/overview");
  revalidatePath("/risk");
}

export async function createClientConfigReleaseAction(formData: FormData) {
  await postAdminOta("/admin/ota/config-releases", {
    versionName: String(formData.get("versionName") ?? "").trim(),
    versionCode: String(formData.get("versionCode") ?? "").trim(),
    targetScope: String(formData.get("targetScope") ?? "").trim(),
    configKey: String(formData.get("configKey") ?? "radio-client-shell").trim(),
    payloadJson: String(formData.get("payloadJson") ?? "{}").trim(),
    rolloutStatus: String(formData.get("rolloutStatus") ?? "draft").trim(),
    applyPolicy: String(formData.get("applyPolicy") ?? "idle_apply").trim(),
    releaseNotes: String(formData.get("releaseNotes") ?? "").trim()
  });

  revalidatePath("/ota");
  revalidatePath("/overview");
}

export async function updateOtaReleaseStatusAction(formData: FormData) {
  await postAdminOta("/admin/ota/releases/status", {
    releaseId: String(formData.get("releaseId") ?? "").trim(),
    rolloutStatus: String(formData.get("rolloutStatus") ?? "paused").trim(),
    rolloutPercent: String(formData.get("rolloutPercent") ?? "").trim(),
    deviceCount: String(formData.get("deviceCount") ?? "").trim(),
    installSuccessRate: String(formData.get("installSuccessRate") ?? "").trim()
  });

  revalidatePath("/ota");
  revalidatePath("/overview");
  revalidatePath("/risk");
}
