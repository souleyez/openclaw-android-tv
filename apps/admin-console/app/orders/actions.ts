"use server";

import { revalidatePath } from "next/cache";
import { getAdminApiBaseUrl } from "@/lib/admin-api";

export async function updateOrderStatusAction(formData: FormData) {
  const response = await fetch(`${getAdminApiBaseUrl()}/admin/orders/status`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json"
    },
    body: JSON.stringify({
      orderId: String(formData.get("orderId") ?? "").trim(),
      status: String(formData.get("status") ?? "reviewing").trim(),
      reviewNote: String(formData.get("reviewNote") ?? "").trim()
    })
  });

  if (!response.ok) {
    throw new Error(`Admin order update failed with ${response.status}`);
  }

  revalidatePath("/orders");
  revalidatePath("/finance");
  revalidatePath("/risk");
  revalidatePath("/overview");
}
