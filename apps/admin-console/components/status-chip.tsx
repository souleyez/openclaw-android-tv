type StatusChipProps = {
  label: string;
  tone?: "default" | "ok" | "warn" | "danger";
};

const statusLabelMap: Record<string, string> = {
  active: "正常",
  disabled: "停用",
  pending: "待处理",
  confirming: "确认中",
  confirmed: "已确认",
  failed: "失败",
  reviewing: "复核中",
  expired: "已过期",
  expiring: "即将到期",
  paused: "已暂停",
  draft: "草稿",
  rolling: "灰度中",
  completed: "已完成",
  rolled_back: "已回滚",
  stable: "稳定",
  beta: "测试",
  internal: "内部",
  control: "控制",
  chat: "对话",
  voice_turn: "语音轮次",
  chat_turn: "对话轮次",
  uploaded: "已上传",
  ready: "可播报",
  system: "系统",
  user: "用户",
  ai: "AI",
  healthy: "健康",
  degraded: "降级",
  unknown: "未知",
  full: "完整",
  basic: "基础",
};

export function StatusChip({ label, tone = "default" }: StatusChipProps) {
  return (
    <span className={`status-pill status-pill--${tone}`}>
      {statusLabelMap[label] ?? label}
    </span>
  );
}
