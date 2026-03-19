import Link from "next/link";
import { Panel } from "@/components/panel";
import { Shell } from "@/components/shell";
import { SimpleTable } from "@/components/simple-table";
import { StatsGrid } from "@/components/stats-grid";
import { StatusChip } from "@/components/status-chip";
import {
  fetchAdminSummary,
  formatDateTime,
  formatDaysLeft,
  formatGenericStatus,
} from "@/lib/admin-api";

export const dynamic = "force-dynamic";

export default async function OverviewPage() {
  const summary = await fetchAdminSummary();
  const activeOrders =
    summary.metrics?.activeOrders ??
    summary.orders.filter((item) =>
      ["pending", "confirming", "reviewing"].includes(item.status),
    ).length;
  const expiringRights = summary.deviceUsers.filter((item) => {
    if (!item.entitlementExpiresAt) {
      return false;
    }

    const daysLeft = Math.ceil(
      (new Date(item.entitlementExpiresAt).getTime() - Date.now()) /
        (1000 * 60 * 60 * 24),
    );
    return daysLeft <= 14;
  });
  const blockedEvents = summary.logs.filter(
    (item) => item.modelProvider === "client_policy_guard",
  );
  const expiringPoolAccounts = summary.apiPoolAccounts.filter((item) => {
    if (item.status === "expiring") {
      return true;
    }
    if (!item.expiresAt) {
      return false;
    }

    const daysLeft = Math.ceil(
      (new Date(item.expiresAt).getTime() - Date.now()) /
        (1000 * 60 * 60 * 24),
    );
    return daysLeft <= 10;
  });
  const transport = summary.metrics?.transport ?? {
    directLease: 0,
    serverFallback: 0,
    offlineLocal: 0,
  };

  return (
    <Shell eyebrow="控制台" title="运营总览">
      <StatsGrid
        items={[
          {
            label: "设备用户",
            value: summary.counts.deviceUsers,
            hint: "已登记的轻账户身份",
            tone: "accent",
          },
          {
            label: "活跃订单",
            value: activeOrders,
            hint: `${summary.counts.orders} 笔累计订单`,
          },
          {
            label: "API 池账户",
            value: summary.counts.apiPoolAccounts,
            hint: `${expiringPoolAccounts.length} 个需要关注`,
            tone: "warn",
          },
          {
            label: "最近日志",
            value: summary.counts.logs,
            hint: "助手、策略与链路事件",
          },
        ]}
      />

      <div className="grid-2">
        <Panel title="链路命中概览" subtitle="优先看直连命中与回退情况。">
          <div className="meta-list">
            <div className="meta-row">
              <span className="meta-row__label">客户端直连租约</span>
              <strong>{transport.directLease}</strong>
            </div>
            <div className="meta-row">
              <span className="meta-row__label">后端模型回退</span>
              <strong>{transport.serverFallback}</strong>
            </div>
            <div className="meta-row">
              <span className="meta-row__label">本地离线路由</span>
              <strong>{transport.offlineLocal}</strong>
            </div>
            <div className="meta-row">
              <span className="meta-row__label">当前激活形象</span>
              <strong>{summary.avatars.find((item) => item.active)?.avatarLabel ?? "-"}</strong>
            </div>
          </div>
        </Panel>

        <Panel title="今日重点" subtitle="封闭测试期优先关注的三件事。">
          <div className="stack">
            <div className="meta-row">
              <span className="meta-row__label">计费模式</span>
              <StatusChip label="封闭测试模式" tone="warn" />
            </div>
            <div className="meta-row">
              <span className="meta-row__label">待跟进订单</span>
              <strong>{activeOrders}</strong>
            </div>
            <div className="meta-row">
              <span className="meta-row__label">策略拦截</span>
              <strong>{blockedEvents.length}</strong>
            </div>
          </div>
        </Panel>
      </div>

      <Panel title="今日告警" subtitle="保持高信号，不堆满大屏。">
        <div className="alert-list">
          <article className="alert-card">
            <h4 className="alert-card__title">权益到期窗口</h4>
            <p className="alert-card__body">
              {expiringRights.length > 0
                ? `未来 14 天内有 ${expiringRights.length} 个设备用户进入续期窗口。`
                : "未来 14 天内没有新的权益到期压力。"}
            </p>
          </article>
          <article className="alert-card">
            <h4 className="alert-card__title">API 池续期风险</h4>
            <p className="alert-card__body">
              {expiringPoolAccounts.length > 0
                ? `当前有 ${expiringPoolAccounts.length} 个模型池账户即将到期，需要手工续期。`
                : "当前没有明显的 API 池续期风险。"}
            </p>
          </article>
          <article className="alert-card">
            <h4 className="alert-card__title">策略拦截</h4>
            <p className="alert-card__body">
              {blockedEvents.length > 0
                ? `最近记录到 ${blockedEvents.length} 条策略拦截，需要确认共享范围或权限配置。`
                : "当前没有新的策略拦截事件。"}
            </p>
          </article>
        </div>
      </Panel>

      <Panel title="快捷入口" subtitle="最常用的值班路径。">
        <div className="grid-quick">
          <Link className="quick-link" href="/risk">
            <p className="quick-link__eyebrow">风险台</p>
            <h4 className="quick-link__title">看续期与异常</h4>
            <p className="quick-link__body">检查到期权益、异常订单、策略拦截和 API 池风险。</p>
          </Link>

          <Link className="quick-link" href="/finance">
            <p className="quick-link__eyebrow">财务台</p>
            <h4 className="quick-link__title">看结算与流水</h4>
            <p className="quick-link__body">查看订单收入、钱包流水和上游订阅续期压力。</p>
          </Link>

          <Link className="quick-link" href="/search">
            <p className="quick-link__eyebrow">搜索台</p>
            <h4 className="quick-link__title">查用户与订单</h4>
            <p className="quick-link__body">按设备用户、订单号、交易哈希统一排查。</p>
          </Link>
        </div>
      </Panel>

      <div className="grid-2">
        <Panel title="即将到期权益" subtitle="优先关注未来两周要续期的设备用户。">
          <SimpleTable
            rows={summary.deviceUsers}
            columns={[
              { key: "user", header: "设备用户", cell: (row) => row.id },
              { key: "plan", header: "套餐", cell: (row) => row.planCode },
              {
                key: "status",
                header: "状态",
                cell: (row) => (
                  <StatusChip
                    label={formatGenericStatus(row.status)}
                    tone={row.status === "active" ? "ok" : "warn"}
                  />
                ),
              },
              {
                key: "expiry",
                header: "到期时间",
                cell: (row) => formatDateTime(row.entitlementExpiresAt),
              },
              {
                key: "days",
                header: "剩余天数",
                cell: (row) => formatDaysLeft(row.entitlementExpiresAt),
              },
            ]}
            emptyLabel="暂无设备用户。"
          />
        </Panel>

        <Panel title="最近路由活动" subtitle="最近的 AI 与策略事件。">
          <SimpleTable
            rows={summary.logs.slice(0, 6)}
            columns={[
              { key: "time", header: "时间", cell: (row) => formatDateTime(row.createdAt) },
              { key: "mode", header: "模式", cell: (row) => row.mode ?? "-" },
              { key: "route", header: "路由", cell: (row) => row.route ?? "-" },
              { key: "user", header: "用户输入", cell: (row) => row.userText ?? "-" },
            ]}
            emptyLabel="暂无助手日志。"
          />
        </Panel>
      </div>
    </Shell>
  );
}
