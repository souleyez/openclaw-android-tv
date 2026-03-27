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
  formatTransportMode,
} from "@/lib/admin-api";

export const dynamic = "force-dynamic";

function getManagedClientScope(id: string): string {
  switch (id) {
    case "sonance-web":
      return "广播目录、广播列表、模型租约、订阅状态";
    case "sonance-android":
      return "广播目录、广播列表、模型租约、录音上传、订阅状态";
    case "sonance-ios":
      return "广播目录、广播列表、模型租约、录音上传、订阅状态";
    default:
      return "广播与模型租约";
  }
}

export default async function OverviewPage() {
  const summary = await fetchAdminSummary();
  const activeOrders =
    summary.metrics?.activeOrders ??
    summary.orders.filter((item) =>
      ["pending", "confirming", "reviewing"].includes(item.status),
    ).length;
  const transport = summary.metrics?.transport ?? {
    directLease: 0,
    serverFallback: 0,
    offlineLocal: 0,
  };

  return (
    <Shell eyebrow="Sonance" title="运营总览">
      <StatsGrid
        items={[
          {
            label: "管理客户端",
            value: summary.managedClients.length,
            hint: summary.managedClients.map((item) => item.platform).join(" / "),
            tone: "accent",
          },
          {
            label: "广播条目",
            value: summary.counts.broadcasts ?? summary.recentBroadcasts.length,
            hint: "最近 AI、用户和系统广播",
          },
          {
            label: "电台目录",
            value: summary.counts.radioStations ?? 0,
            hint: "当前本地可用公开电台",
          },
          {
            label: "活跃租约",
            value: summary.metrics?.activeModelLeases ?? 0,
            hint: "当前占用中的模型权限",
            tone: "warn",
          },
        ]}
      />

      <div className="grid-2">
        <Panel title="当前管理范围" subtitle="明确这套后台当前服务哪些客户端。">
          <SimpleTable
            rows={summary.managedClients}
            columns={[
              { key: "client", header: "客户端", cell: (row) => row.label },
              { key: "platform", header: "平台", cell: (row) => row.platform },
              { key: "scope", header: "管理范围", cell: (row) => getManagedClientScope(row.id) },
              {
                key: "status",
                header: "状态",
                cell: (row) => <StatusChip label={row.status} tone="ok" />,
              },
            ]}
          />
        </Panel>

        <Panel title="链路命中概览" subtitle="优先看本地优先和模型租约的落点。">
          <div className="meta-list">
            <div className="meta-row">
              <span className="meta-row__label">
                {formatTransportMode("client_direct_provider_lease")}
              </span>
              <strong>{transport.directLease}</strong>
            </div>
            <div className="meta-row">
              <span className="meta-row__label">
                {formatTransportMode("server_router_fallback")}
              </span>
              <strong>{transport.serverFallback}</strong>
            </div>
            <div className="meta-row">
              <span className="meta-row__label">{formatTransportMode("offline_local")}</span>
              <strong>{transport.offlineLocal}</strong>
            </div>
            <div className="meta-row">
              <span className="meta-row__label">活跃订单</span>
              <strong>{activeOrders}</strong>
            </div>
          </div>
        </Panel>
      </div>

      <Panel title="最近广播列表" subtitle="这里直接看最近生成或上传的广播条目。">
        <SimpleTable
          rows={summary.recentBroadcasts}
          columns={[
            { key: "title", header: "标题", cell: (row) => row.title },
            {
              key: "source",
              header: "来源",
              cell: (row) => (
                <StatusChip
                  label={row.sourceKind}
                  tone={row.sourceKind === "ai" ? "ok" : "default"}
                />
              ),
            },
            { key: "station", header: "所属电台", cell: (row) => row.stationName ?? "-" },
            {
              key: "status",
              header: "状态",
              cell: (row) => (
                <StatusChip
                  label={row.status}
                  tone={
                    row.status === "ready"
                      ? "ok"
                      : row.status === "failed"
                        ? "danger"
                        : "warn"
                  }
                />
              ),
            },
            { key: "account", header: "账户", cell: (row) => row.accountId },
            { key: "createdAt", header: "创建时间", cell: (row) => formatDateTime(row.createdAt) },
          ]}
          emptyLabel="暂无广播条目"
        />
      </Panel>

      <div className="grid-2">
        <Panel title="最近订单" subtitle="保留值班最常看的部分。">
          <SimpleTable
            rows={summary.orders.slice(0, 6)}
            columns={[
              { key: "accountId", header: "账户", cell: (row) => row.accountId },
              {
                key: "asset",
                header: "资产",
                cell: (row) => `${row.stablecoinSymbol} / ${row.chain}`,
              },
              {
                key: "status",
                header: "状态",
                cell: (row) => (
                  <StatusChip
                    label={row.status}
                    tone={row.status === "confirmed" ? "ok" : "warn"}
                  />
                ),
              },
              {
                key: "expiresAt",
                header: "过期时间",
                cell: (row) => formatDateTime(row.expiresAt),
              },
            ]}
            emptyLabel="暂无订单"
          />
        </Panel>

        <Panel title="设备用户" subtitle="聚焦近期要跟进的续期和状态。">
          <SimpleTable
            rows={summary.deviceUsers.slice(0, 6)}
            columns={[
              { key: "id", header: "设备用户", cell: (row) => row.id },
              { key: "plan", header: "套餐", cell: (row) => row.planCode },
              {
                key: "status",
                header: "状态",
                cell: (row) => (
                  <StatusChip
                    label={row.status}
                    tone={row.status === "active" ? "ok" : "warn"}
                  />
                ),
              },
              { key: "days", header: "剩余天数", cell: (row) => formatDaysLeft(row.entitlementExpiresAt) },
            ]}
            emptyLabel="暂无设备用户"
          />
        </Panel>
      </div>

      <Panel title="快捷入口" subtitle="保留常用管理路径。">
        <div className="grid-quick">
          <Link className="quick-link" href="/radio">
            <p className="quick-link__eyebrow">节目源</p>
            <h4 className="quick-link__title">管理节目源与广播</h4>
            <p className="quick-link__body">搜索公开电台、手工新增节目源，并查看最近广播列表。</p>
          </Link>
          <Link className="quick-link" href="/risk">
            <p className="quick-link__eyebrow">风险台</p>
            <h4 className="quick-link__title">查看续期与异常</h4>
            <p className="quick-link__body">检查到期权益、异常订单和模型池风险。</p>
          </Link>
          <Link className="quick-link" href="/logs">
            <p className="quick-link__eyebrow">日志台</p>
            <h4 className="quick-link__title">查看最近行为</h4>
            <p className="quick-link__body">追踪最近的交互、路由和失败事件。</p>
          </Link>
        </div>
      </Panel>
    </Shell>
  );
}
