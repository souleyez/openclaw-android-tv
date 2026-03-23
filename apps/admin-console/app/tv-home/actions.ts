"use server";

import { revalidatePath } from "next/cache";
import { getAdminApiBaseUrl } from "@/lib/admin-api";

export async function saveTvHomeConfigAction(formData: FormData) {
  const featuredAppIds = String(formData.get("featuredAppIds") ?? "")
    .split(/[\s,]+/)
    .map((item) => item.trim())
    .filter((item) => item.length > 0);

  const response = await fetch(`${getAdminApiBaseUrl()}/admin/tv-home-configs`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
    },
    body: JSON.stringify({
      id: String(formData.get("id") ?? "").trim() || undefined,
      countryCode: String(formData.get("countryCode") ?? "GLOBAL").trim(),
      regionCode: String(formData.get("regionCode") ?? "").trim() || undefined,
      backgroundImageUrl:
        String(formData.get("backgroundImageUrl") ?? "").trim() || undefined,
      featuredAppIds,
      status: String(formData.get("status") ?? "active").trim(),
    }),
  });

  if (!response.ok) {
    throw new Error(`Admin tv home update failed with ${response.status}`);
  }

  revalidatePath("/tv-home");
}
