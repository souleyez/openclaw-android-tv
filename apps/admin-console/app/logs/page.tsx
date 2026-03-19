import { Panel } from "@/components/panel";
import { Shell } from "@/components/shell";
import { SimpleTable } from "@/components/simple-table";
import { StatsGrid } from "@/components/stats-grid";
import { StatusChip } from "@/components/status-chip";
import {
  fetchAdminLogs,
  fetchAdminSummary,
  formatDateTime,
  formatNumber,
  formatTransportMode,
} from "@/lib/admin-api";

export const dynamic = "force-dynamic";

type LogsPageProps = {
  searchParams?: Promise<{
    q?: string;
    mode?: string;
    transportMode?: string;
    page?: string;
  }>;
};

export default async function LogsPage({ searchParams }: LogsPageProps) {
  const filters = (await searchParams) ?? {};
  const page = Math.max(1, Number.parseInt(filters.page ?? "1", 10) || 1);
  const [summary, logs] = await Promise.all([
    fetchAdminSummary(),
    fetchAdminLogs({
      q: filters.q,
      mode: filters.mode,
      transportMode: filters.transportMode,
      page,
      pageSize: 12,
    }),
  ]);

  const allLogs = summary.logs;
  const items = logs.items;
  const controlLogs = allLogs.filter((item) => item.mode === "control").length;
  const blockedLogs = allLogs.filter(
    (item) => item.modelProvider === "client_policy_guard",
  ).length;
  const directLeaseLogs = allLogs.filter(
    (item) => item.transportMode === "client_direct_provider_lease",
  ).length;
  const tokenSpend = allLogs.reduce((sum, item) => sum + (item.tokenUsage ?? 0), 0);

  return (
    <Shell eyebrow="观测" title="日志与审计">
      <StatsGrid
        items={[
          {
            label: "日志事件",
            value: logs.total,
            hint: "当前筛选窗口内的助手事件",
            tone: "accent",
          },
          {
            label: "控制事件",
            value: controlLogs,
            hint: "尝试控制设备的动作数",
          },
          {
            label: "策略拦截",
            value: blockedLogs,
            hint: "权限或共享范围拦截",
            tone: "warn",
          },
          {
            label: "直连租约",
            value: directLeaseLogs,
            hint: `最近日志累计消耗 ${formatNumber(tokenSpend)} token`,
          },
        ]}
      />

      <Panel title="最近助手事件" subtitle="按用户、模式、链路和路由排查当前行为。">
        <form className="toolbar-form" method="get">
          <div className="toolbar-form__field">
            <label className="toolbar-form__label" htmlFor="logs-q">
              搜索
            </label>
            <input
              className="toolbar-form__input"
              defaultValue={filters.q ?? ""}
              id="logs-q"
              name="q"
              placeholder="设备用户 / 路由 / 动作 / 提供商"
            />
          </div>
          <div className="toolbar-form__field">
            <label className="toolbar-form__label" htmlFor="logs-mode">
              模式
            </label>
            <select className="toolbar-form__select" defaultValue={filters.mode ?? ""} id="logs-mode" name="mode">
              <option value="">全部</option>
              <option value="control">控制</option>
              <option value="chat">对话</option>
            </select>
          </div>
          <div className="toolbar-form__field">
            <label className="toolbar-form__label" htmlFor="logs-transport">
              链路
            </label>
            <select
              className="toolbar-form__select"
              defaultValue={filters.transportMode ?? ""}
              id="logs-transport"
              name="transportMode"
            >
              <option value="">全部</option>
              <option value="client_direct_provider_lease">客户端直连租约</option>
              <option value="server_router_fallback">后端模型回退</option>
              <option value="offline_local">本地离线路由</option>
            </select>
          </div>
          <div className="toolbar-form__actions">
            <button className="toolbar-form__button" type="submit">
              应用
            </button>
            <a className="toolbar-form__link" href="/logs">
              重置
            </a>
          </div>
        </form>

        <SimpleTable
          rows={items}
          columns={[
            { key: "time", header: "时间", cell: (row) => formatDateTime(row.createdAt) },
            { key: "account", header: "设备用户", cell: (row) => row.accountId ?? "-" },
            { key: "mode", header: "模式", cell: (row) => row.mode ?? "-" },
            {
              key: "transport",
              header: "链路",
              cell: (row) => formatTransportMode(row.transportMode),
            },
            { key: "route", header: "路由", cell: (row) => row.route ?? "-" },
            { key: "action", header: "动作", cell: (row) => row.action ?? "-" },
            { key: "input", header: "用户输入", cell: (row) => row.userText ?? "-" },
          ]}
          emptyLabel="当前筛选下没有日志。"
        />

        <div className="pagination-bar">
          <span>
            显示 {items.length} / {logs.total} 条 · 第 {logs.page} 页
          </span>
          <div className="pagination-bar__actions">
            <a
              className={`pagination-bar__link${logs.page <= 1 ? " pagination-bar__link--disabled" : ""}`}
              href={`/logs?q=${encodeURIComponent(filters.q ?? "")}&mode=${encodeURIComponent(filters.mode ?? "")}&transportMode=${encodeURIComponent(filters.transportMode ?? "")}&page=${Math.max(1, logs.page - 1)}`}
            >
              上一页
            </a>
            <a
              className={`pagination-bar__link${logs.page * logs.pageSize >= logs.total ? " pagination-bar__link--disabled" : ""}`}
              href={`/logs?q=${encodeURIComponent(filters.q ?? "")}&mode=${encodeURIComponent(filters.mode ?? "")}&transportMode=${encodeURIComponent(filters.transportMode ?? "")}&page=${logs.page + 1}`}
            >
              下一页
            </a>
          </div>
        </div>
      </Panel>

      <div className="grid-2">
        <Panel title="链路说明" subtitle="方便值班同学快速理解日志来源。">
          <div className="meta-list">
            <div className="meta-row">
              <span className="meta-row__label">客户端直连租约</span>
              <strong>客户端拿临时凭证直连模型</strong>
            </div>
            <div className="meta-row">
              <span className="meta-row__label">后端模型回退</span>
              <strong>客户端直连失败后走服务器路由</strong>
            </div>
            <div className="meta-row">
              <span className="meta-row__label">本地离线路由</span>
              <strong>断网时仅使用本地控制链</strong>
            </div>
          </div>
        </Panel>

        <Panel title="策略拦截摘要" subtitle="最近被范围或权限挡下来的请求。">
          <SimpleTable
            rows={allLogs.filter((item) => item.modelProvider === "client_policy_guard").slice(0, 6)}
            columns={[
              { key: "time", header: "时间", cell: (row) => formatDateTime(row.createdAt) },
              { key: "account", header: "设备用户", cell: (row) => row.accountId ?? "-" },
              { key: "route", header: "路由", cell: (row) => row.route ?? "-" },
              {
                key: "status",
                header: "状态",
                cell: () => <StatusChip label="已拦截" tone="warn" />,
              },
              { key: "input", header: "用户输入", cell: (row) => row.userText ?? "-" },
            ]}
            emptyLabel="暂无新的策略拦截。"
          />
        </Panel>
      </div>
    </Shell>
  );
}
