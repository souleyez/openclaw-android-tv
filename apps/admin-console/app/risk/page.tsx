import { Panel } from "@/components/panel";
import { Shell } from "@/components/shell";
import { SimpleTable } from "@/components/simple-table";
import { StatsGrid } from "@/components/stats-grid";
import { StatusChip } from "@/components/status-chip";
import {
  fetchAdminRiskSnapshot,
  formatDateTime,
  formatDaysLeft,
  formatGenericStatus,
} from "@/lib/admin-api";

export const dynamic = "force-dynamic";

export default async function RiskPage() {
  const risk = await fetchAdminRiskSnapshot();

  return (
    <Shell eyebrow="风险" title="续期与异常风险">
      <StatsGrid
        items={[
          {
            label: "即将到期权益",
            value: risk.metrics.expiringRights,
            hint: "14 天内到期的设备用户",
            tone: "warn",
          },
          {
            label: "失败订单",
            value: risk.metrics.failedOrders,
            hint: "失败或已过期的稳定币订单",
          },
          {
            label: "复核队列",
            value: risk.metrics.reviewOrders,
            hint: "等待人工处理的订单",
          },
          {
            label: "策略拦截",
            value: risk.metrics.blockedEvents,
            hint: "最近的权限或范围拦截",
            tone: "accent",
          },
        ]}
      />

      <div className="grid-2">
        <Panel title="模型链路风险" subtitle="快速看最近流量落在哪条链路上。">
          <div className="meta-list">
            <div className="meta-row">
              <span className="meta-row__label">客户端直连租约</span>
              <strong>{risk.metrics.directLease}</strong>
            </div>
            <div className="meta-row">
              <span className="meta-row__label">后端模型回退</span>
              <strong>{risk.metrics.serverFallback}</strong>
            </div>
            <div className="meta-row">
              <span className="meta-row__label">本地离线路由</span>
              <strong>{risk.metrics.offlineLocal}</strong>
            </div>
            <div className="meta-row">
              <span className="meta-row__label">API 池续期风险</span>
              <strong>{risk.metrics.expiringApiPoolAccounts}</strong>
            </div>
          </div>
        </Panel>

        <Panel title="API 池风险" subtitle="可能影响模型可用性和成本的上游账户。">
          <SimpleTable
            rows={risk.expiringApiPoolAccounts}
            columns={[
              { key: "provider", header: "提供商", cell: (row) => row.provider },
              { key: "account", header: "账户", cell: (row) => row.accountLabel },
              {
                key: "status",
                header: "状态",
                cell: (row) => (
                  <StatusChip label={formatGenericStatus(row.status)} tone="warn" />
                ),
              },
              { key: "renews", header: "续期时间", cell: (row) => formatDateTime(row.renewsAt) },
              { key: "expires", header: "到期时间", cell: (row) => formatDateTime(row.expiresAt) },
            ]}
            emptyLabel="当前没有检测到 API 池续期风险。"
          />
        </Panel>
      </div>

      <div className="grid-2">
        <Panel title="即将到期权益" subtitle="近期需要续期或恢复引导的设备用户。">
          <SimpleTable
            rows={risk.expiringRights}
            columns={[
              { key: "id", header: "设备用户", cell: (row) => <span className="mono">{row.id}</span> },
              { key: "plan", header: "套餐", cell: (row) => row.planCode },
              {
                key: "status",
                header: "状态",
                cell: (row) => (
                  <StatusChip label={formatGenericStatus(row.status)} tone="warn" />
                ),
              },
              { key: "expires", header: "到期时间", cell: (row) => formatDateTime(row.entitlementExpiresAt) },
              { key: "days", header: "剩余天数", cell: (row) => formatDaysLeft(row.entitlementExpiresAt) },
            ]}
            emptyLabel="当前窗口内没有即将到期权益。"
          />
        </Panel>

        <Panel title="失败与过期订单" subtitle="可能需要客服介入或引导重试的支付事件。">
          <SimpleTable
            rows={risk.failedOrders}
            columns={[
              { key: "id", header: "订单号", cell: (row) => <span className="mono">{row.id}</span> },
              { key: "account", header: "设备用户", cell: (row) => row.accountId },
              { key: "asset", header: "资产", cell: (row) => `${row.stablecoinSymbol} / ${row.chain}` },
              {
                key: "status",
                header: "状态",
                cell: (row) => (
                  <StatusChip label={formatGenericStatus(row.status)} tone="danger" />
                ),
              },
              { key: "note", header: "备注", cell: (row) => row.reviewNote ?? "-" },
            ]}
            emptyLabel="暂无失败或过期订单。"
          />
        </Panel>
      </div>

      <div className="grid-2">
        <Panel title="复核队列" subtitle="仍在等待人工复核的订单。">
          <SimpleTable
            rows={risk.reviewOrders}
            columns={[
              { key: "id", header: "订单号", cell: (row) => row.id },
              { key: "account", header: "设备用户", cell: (row) => row.accountId },
              {
                key: "status",
                header: "状态",
                cell: (row) => (
                  <StatusChip label={formatGenericStatus(row.status)} tone="warn" />
                ),
              },
              { key: "tx", header: "Tx Hash", cell: (row) => row.txHash ?? "-" },
              { key: "updated", header: "更新时间", cell: (row) => formatDateTime(row.updatedAt) },
            ]}
            emptyLabel="暂无待人工复核订单。"
          />
        </Panel>

        <Panel title="策略拦截事件" subtitle="用户触发了动作，但权限或共享范围阻止了执行。">
          <SimpleTable
            rows={risk.blockedEvents}
            columns={[
              { key: "time", header: "时间", cell: (row) => formatDateTime(row.createdAt) },
              { key: "account", header: "设备用户", cell: (row) => row.accountId ?? "-" },
              { key: "route", header: "路由", cell: (row) => row.route ?? "-" },
              { key: "action", header: "动作", cell: (row) => row.action ?? "-" },
              { key: "user", header: "用户输入", cell: (row) => row.userText ?? "-" },
            ]}
            emptyLabel="暂无策略拦截事件。"
          />
        </Panel>
      </div>
    </Shell>
  );
}
