"use server";

import { revalidatePath } from "next/cache";
import { getAdminApiBaseUrl } from "@/lib/admin-api";

async function postAdminRadio(path: string, payload: Record<string, string>) {
  const response = await fetch(`${getAdminApiBaseUrl()}${path}`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
    },
    body: JSON.stringify(payload),
  });

  if (!response.ok) {
    throw new Error(`Admin radio write failed with ${response.status}`);
  }
}

export async function addRadioSourceFromSearchAction(formData: FormData) {
  await postAdminRadio("/admin/radio/source-add", {
    externalId: String(formData.get("externalId") ?? "").trim(),
    name: String(formData.get("name") ?? "").trim(),
    countryCode: String(formData.get("countryCode") ?? "").trim(),
    regionCode: String(formData.get("regionCode") ?? "").trim(),
    city: String(formData.get("city") ?? "").trim(),
    language: String(formData.get("language") ?? "").trim(),
    genre: String(formData.get("genre") ?? "").trim(),
    streamUrl: String(formData.get("streamUrl") ?? "").trim(),
    homepageUrl: String(formData.get("homepageUrl") ?? "").trim(),
    logoUrl: String(formData.get("logoUrl") ?? "").trim(),
  });

  revalidatePath("/radio");
  revalidatePath("/overview");
}

export async function addManualRadioSourceAction(formData: FormData) {
  await postAdminRadio("/admin/radio/source-manual", {
    id: String(formData.get("id") ?? "").trim(),
    name: String(formData.get("name") ?? "").trim(),
    countryCode: String(formData.get("countryCode") ?? "").trim(),
    regionCode: String(formData.get("regionCode") ?? "").trim(),
    city: String(formData.get("city") ?? "").trim(),
    language: String(formData.get("language") ?? "").trim(),
    bandLabel: String(formData.get("bandLabel") ?? "").trim(),
    genre: String(formData.get("genre") ?? "").trim(),
    streamUrl: String(formData.get("streamUrl") ?? "").trim(),
    homepageUrl: String(formData.get("homepageUrl") ?? "").trim(),
    logoUrl: String(formData.get("logoUrl") ?? "").trim(),
    legalNotes: String(formData.get("legalNotes") ?? "").trim(),
  });

  revalidatePath("/radio");
  revalidatePath("/overview");
}
