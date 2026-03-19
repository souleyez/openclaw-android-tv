import { updateOrderStatusAction } from "@/app/orders/actions";
import { Panel } from "@/components/panel";
import { Shell } from "@/components/shell";
import { SimpleTable } from "@/components/simple-table";
import { StatsGrid } from "@/components/stats-grid";
import { StatusChip } from "@/components/status-chip";
import {
  fetchAdminOrders,
  fetchAdminSummary,
  formatDateTime,
  formatGenericStatus,
} from "@/lib/admin-api";

export const dynamic = "force-dynamic";

type OrdersPageProps = {
  searchParams?: Promise<{ q?: string; status?: string; page?: string }>;
};

export default async function OrdersPage({ searchParams }: OrdersPageProps) {
  const filters = (await searchParams) ?? {};
  const page = Math.max(1, Number.parseInt(filters.page ?? "1", 10) || 1);
  const [summary, orders] = await Promise.all([
    fetchAdminSummary(),
    fetchAdminOrders({ q: filters.q, status: filters.status, page, pageSize: 12 }),
  ]);
  const allOrders = summary.orders;
  const items = orders.items;
  const confirming = allOrders.filter((item) => item.status === "confirming").length;
  const reviewing = allOrders.filter((item) => item.status === "reviewing").length;
  const confirmed = allOrders.filter((item) => item.status === "confirmed").length;

  return (
    <Shell eyebrow="计费" title="订单与结算">
      <StatsGrid
        items={[
          {
            label: "订单总数",
            value: orders.total,
            hint: "稳定币测试计费订单",
            tone: "accent",
          },
          {
            label: "确认中",
            value: confirming,
            hint: "等待链上确认",
          },
          {
            label: "复核中",
            value: reviewing,
            hint: "等待人工处理",
            tone: "warn",
          },
          {
            label: "已确认",
            value: confirmed,
            hint: "已落到账户流水",
          },
        ]}
      />

      <div className="grid-2">
        <Panel title="最近订单" subtitle="查看订单生命周期、链上进度与人工备注。">
          <form className="toolbar-form" method="get">
            <div className="toolbar-form__field">
              <label className="toolbar-form__label" htmlFor="orders-q">
                搜索
              </label>
              <input
                className="toolbar-form__input"
                defaultValue={filters.q ?? ""}
                id="orders-q"
                name="q"
                placeholder="orderId / txHash / 设备用户 / 链名"
              />
            </div>
            <div className="toolbar-form__field">
              <label className="toolbar-form__label" htmlFor="orders-status">
                状态
              </label>
              <select className="toolbar-form__select" defaultValue={filters.status ?? ""} id="orders-status" name="status">
                <option value="">全部</option>
                <option value="confirming">确认中</option>
                <option value="reviewing">复核中</option>
                <option value="confirmed">已确认</option>
                <option value="failed">失败</option>
                <option value="expired">已过期</option>
              </select>
            </div>
            <div className="toolbar-form__actions">
              <button className="toolbar-form__button" type="submit">
                应用
              </button>
              <a className="toolbar-form__link" href="/orders">
                重置
              </a>
            </div>
          </form>

          <SimpleTable
            rows={items}
            columns={[
              { key: "id", header: "订单号", cell: (row) => <span className="mono">{row.id}</span> },
              { key: "account", header: "设备用户", cell: (row) => row.accountId },
              { key: "asset", header: "资产", cell: (row) => `${row.stablecoinSymbol} / ${row.chain}` },
              {
                key: "status",
                header: "状态",
                cell: (row) => (
                  <StatusChip
                    label={formatGenericStatus(row.status)}
                    tone={
                      row.status === "confirmed"
                        ? "ok"
                        : row.status === "confirming" || row.status === "reviewing"
                          ? "warn"
                          : "danger"
                    }
                  />
                ),
              },
              { key: "amount", header: "金额", cell: (row) => `$${row.amountUsd}` },
              { key: "confirmations", header: "确认数", cell: (row) => row.confirmations ?? 0 },
              { key: "created", header: "创建时间", cell: (row) => formatDateTime(row.createdAt) },
              {
                key: "finance",
                header: "财务页",
                cell: (row) => (
                  <a className="result-link" href={`/finance?q=${encodeURIComponent(row.id)}`}>
                    打开财务
                  </a>
                ),
              },
            ]}
            emptyLabel="当前筛选下没有订单。"
          />

          <div className="pagination-bar">
            <span>
              显示 {items.length} / {orders.total} 条 · 第 {orders.page} 页
            </span>
            <div className="pagination-bar__actions">
              <a
                className={`pagination-bar__link${orders.page <= 1 ? " pagination-bar__link--disabled" : ""}`}
                href={`/orders?q=${encodeURIComponent(filters.q ?? "")}&status=${encodeURIComponent(filters.status ?? "")}&page=${Math.max(1, orders.page - 1)}`}
              >
                上一页
              </a>
              <a
                className={`pagination-bar__link${orders.page * orders.pageSize >= orders.total ? " pagination-bar__link--disabled" : ""}`}
                href={`/orders?q=${encodeURIComponent(filters.q ?? "")}&status=${encodeURIComponent(filters.status ?? "")}&page=${orders.page + 1}`}
              >
                下一页
              </a>
            </div>
          </div>
        </Panel>

        <div className="stack">
          <Panel title="人工结算操作" subtitle="手工调整订单状态并留下处理备注。">
            <form action={updateOrderStatusAction} className="stack-form">
              <div className="toolbar-form__field">
                <label className="toolbar-form__label" htmlFor="manual-order-id">
                  订单 ID
                </label>
                <input className="toolbar-form__input" id="manual-order-id" name="orderId" placeholder="order_123" />
              </div>
              <div className="toolbar-form__field">
                <label className="toolbar-form__label" htmlFor="manual-order-status">
                  状态
                </label>
                <select className="toolbar-form__select" defaultValue="reviewing" id="manual-order-status" name="status">
                  <option value="pending">待处理</option>
                  <option value="confirming">确认中</option>
                  <option value="reviewing">复核中</option>
                  <option value="confirmed">已确认</option>
                  <option value="failed">失败</option>
                  <option value="expired">已过期</option>
                </select>
              </div>
              <div className="toolbar-form__field">
                <label className="toolbar-form__label" htmlFor="manual-review-note">
                  备注
                </label>
                <textarea className="toolbar-form__input" id="manual-review-note" name="reviewNote" placeholder="人工处理说明" />
              </div>
              <div className="toolbar-form__actions">
                <button className="toolbar-form__button" type="submit">
                  保存订单
                </button>
              </div>
            </form>
          </Panel>

          <Panel title="人工复核队列" subtitle="优先查看尚未确认的订单。">
            <SimpleTable
              rows={allOrders.filter((item) => item.status !== "confirmed").slice(0, 8)}
              columns={[
                { key: "order", header: "订单", cell: (row) => row.id },
                { key: "status", header: "状态", cell: (row) => formatGenericStatus(row.status) },
                { key: "tx", header: "Tx Hash", cell: (row) => row.txHash ?? "-" },
                { key: "note", header: "备注", cell: (row) => row.reviewNote ?? "-" },
                {
                  key: "finance",
                  header: "财务页",
                  cell: (row) => (
                    <a className="result-link" href={`/finance?q=${encodeURIComponent(row.id)}`}>
                      查看
                    </a>
                  ),
                },
              ]}
              emptyLabel="暂无复核项。"
            />
          </Panel>
        </div>
      </div>
    </Shell>
  );
}
