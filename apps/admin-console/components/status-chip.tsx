type StatusChipProps = {
  label: string;
  tone?: "default" | "ok" | "warn" | "danger";
};

const statusLabelMap: Record<string, string> = {
  active: "Active",
  disabled: "Disabled",
  pending: "Pending",
  confirming: "Confirming",
  confirmed: "Confirmed",
  failed: "Failed",
  reviewing: "Reviewing",
  expired: "Expired",
  expiring: "Expiring",
  paused: "Paused",
  draft: "Draft",
  rolling: "Rolling",
  completed: "Completed",
  rolled_back: "Rolled back",
  stable: "Stable",
  beta: "Beta",
  internal: "Internal",
  control: "Control",
  chat: "Chat",
  voice_turn: "Voice turn",
  chat_turn: "Chat turn",
  uploaded: "Uploaded",
  ready: "Ready",
  system: "System",
  user: "User",
  ai: "AI",
  healthy: "Healthy",
  degraded: "Degraded",
  unknown: "Unknown",
  full: "Full",
  basic: "Basic",
};

export function StatusChip({ label, tone = "default" }: StatusChipProps) {
  return (
    <span className={`status-pill status-pill--${tone}`}>
      {statusLabelMap[label] ?? label}
    </span>
  );
}
