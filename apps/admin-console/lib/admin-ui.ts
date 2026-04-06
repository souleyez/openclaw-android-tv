export const adminNavItems = [
  { href: "/tv-home", label: "TV Home" },
  { href: "/overview", label: "总览" },
  { href: "/radio", label: "节目源" },
  { href: "/search", label: "全局搜索" },
  { href: "/finance", label: "财务" },
  { href: "/risk", label: "风险" },
  { href: "/ota", label: "OTA 与版本" },
  { href: "/device-users", label: "设备用户" },
  { href: "/api-pool", label: "模型 API 池" },
  { href: "/orders", label: "订单" },
  { href: "/logs", label: "日志与审计" },
] as const;

export function formatDaysLeft(value?: string): string {
  if (!value) {
    return "-";
  }

  const target = new Date(value).getTime();
  if (Number.isNaN(target)) {
    return "-";
  }

  const diffMs = target - Date.now();
  const diffDays = Math.ceil(diffMs / (1000 * 60 * 60 * 24));
  return `${diffDays} 天`;
}

export function formatTransportMode(value?: string): string {
  switch (value) {
    case "client_direct_provider_lease":
      return "客户端直连租约";
    case "server_router_fallback":
      return "后端路由回退";
    case "offline_local":
      return "本地离线路由";
    default:
      return value ?? "-";
  }
}

export function formatGenericStatus(value?: string): string {
  switch (value) {
    case "active":
      return "正常";
    case "disabled":
      return "停用";
    case "expiring":
      return "即将到期";
    case "expired":
      return "已过期";
    case "paused":
      return "已暂停";
    case "pending":
      return "待处理";
    case "confirming":
      return "确认中";
    case "reviewing":
      return "复核中";
    case "confirmed":
      return "已确认";
    case "failed":
      return "失败";
    case "draft":
      return "草稿";
    case "rolling":
      return "灰度中";
    case "completed":
      return "已完成";
    case "rolled_back":
      return "已回滚";
    case "uploaded":
      return "已上传";
    case "ready":
      return "可播报";
    default:
      return value ?? "-";
  }
}
