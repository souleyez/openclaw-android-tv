import { Panel } from "@/components/panel";
import { Shell } from "@/components/shell";
import { SimpleTable } from "@/components/simple-table";
import { StatsGrid } from "@/components/stats-grid";
import { StatusChip } from "@/components/status-chip";
import { fetchAdminFinanceSnapshot, formatDateTime, formatNumber } from "@/lib/admin-api";
import { formatGenericStatus } from "@/lib/admin-ui";

export const dynamic = "force-dynamic";

type FinancePageProps = {
  searchParams?: Promise<{ q?: string; status?: string }>;
};

export default async function FinancePage({ searchParams }: FinancePageProps) {
  const finance = await fetchAdminFinanceSnapshot();
  const filters = (await searchParams) ?? {};
  const query = (filters.q ?? "").trim().toLowerCase();
  const status = (filters.status ?? "").trim().toLowerCase();

  const filteredOrders = finance.recentOrders.filter((item) => {
    const matchesStatus = !status || item.status.toLowerCase() === status;
    const haystack = [
      item.id,
      item.accountId,
      item.stablecoinSymbol,
      item.chain,
      item.txHash,
      item.walletAddress,
    ]
      .filter(Boolean)
      .join(" ")
      .toLowerCase();
    return matchesStatus && (!query || haystack.includes(query));
  });

  const filteredLedger = finance.walletLedger.filter((item) => {
    const haystack = [
      item.id,
      item.accountId,
      item.entryType,
      item.currency,
      item.source,
      item.referenceId,
      item.note,
    ]
      .filter(Boolean)
      .join(" ")
      .toLowerCase();
    return !query || haystack.includes(query);
  });

  return (
    <Shell eyebrow="Finance" title="收入与结算">
      <StatsGrid
        items={[
          {
            label: "已结算收入",
            value: `$${formatNumber(finance.metrics.settledRevenueUsd)}`,
            hint: `${finance.metrics.confirmedOrders} 笔已确认订单`,
            tone: "accent",
          },
          {
            label: "待结算收入",
            value: `$${formatNumber(finance.metrics.pendingRevenueUsd)}`,
            hint: "确认中、复核中与待处理订单",
          },
          {
            label: "已发放 Token",
            value: formatNumber(finance.metrics.tokenGranted),
            hint: "所有正向 token 流水",
          },
          {
            label: "API 池续期",
            value: finance.metrics.apiPoolExpiring,
            hint: "即将到期的模型池账户",
            tone: "warn",
          },
        ]}
      />

      <div className="grid-2">
        <Panel title="最近结算订单" subtitle="查看支付、确认和结算进度。">
          <form className="toolbar-form" method="get">
            <div className="toolbar-form__field">
              <label className="toolbar-form__label" htmlFor="finance-q">搜索</label>
              <input
                className="toolbar-form__input"
                defaultValue={filters.q ?? ""}
                id="finance-q"
                name="q"
                placeholder="orderId / txHash / 设备用户 / wallet ref"
              />
            </div>
            <div className="toolbar-form__field">
              <label className="toolbar-form__label" htmlFor="finance-status">状态</label>
              <select
                className="toolbar-form__select"
                defaultValue={filters.status ?? ""}
                id="finance-status"
                name="status"
              >
                <option value="">全部</option>
                <option value="pending">待处理</option>
                <option value="confirming">确认中</option>
                <option value="reviewing">复核中</option>
                <option value="confirmed">已确认</option>
                <option value="failed">失败</option>
                <option value="expired">已过期</option>
              </select>
            </div>
            <div className="toolbar-form__actions">
              <button className="toolbar-form__button" type="submit">应用</button>
              <a className="toolbar-form__link" href="/finance">重置</a>
            </div>
          </form>

          <SimpleTable
            rows={filteredOrders}
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
                          : "default"
                    }
                  />
                ),
              },
              { key: "amount", header: "金额", cell: (row) => `$${row.amountUsd}` },
              { key: "tx", header: "Tx Hash", cell: (row) => row.txHash ?? "-" },
              {
                key: "order",
                header: "订单页",
                cell: (row) => (
                  <a className="result-link" href={`/orders?q=${encodeURIComponent(row.id)}`}>
                    打开订单
                  </a>
                ),
              },
            ]}
            emptyLabel="暂无匹配订单。"
          />
        </Panel>

        <div className="stack">
          <Panel title="财务概览" subtitle="当前阶段的精简财务视角。">
            <div className="meta-list">
              <div className="meta-row"><span className="meta-row__label">订单总数</span><strong>{finance.metrics.totalOrders}</strong></div>
              <div className="meta-row"><span className="meta-row__label">已确认订单</span><strong>{finance.metrics.confirmedOrders}</strong></div>
              <div className="meta-row"><span className="meta-row__label">已使用 Token</span><strong>{formatNumber(finance.metrics.tokenUsed)}</strong></div>
              <div className="meta-row"><span className="meta-row__label">即将到期 API 池</span><strong>{finance.metrics.apiPoolExpiring}</strong></div>
            </div>
          </Panel>

          <Panel title="续期观察列表" subtitle="可能影响模型容量与成本的上游订阅。">
            <SimpleTable
              rows={finance.apiPoolAccounts}
              columns={[
                { key: "provider", header: "提供商", cell: (row) => row.provider },
                { key: "account", header: "账户", cell: (row) => row.accountLabel },
                { key: "plan", header: "套餐", cell: (row) => row.planLabel },
                { key: "status", header: "状态", cell: (row) => formatGenericStatus(row.status) },
                { key: "expires", header: "到期时间", cell: (row) => formatDateTime(row.expiresAt) },
              ]}
              emptyLabel="暂无 API 池账户。"
            />
          </Panel>
        </div>
      </div>

      <Panel title="钱包流水" subtitle="最近的 token 发放、结算、用量与人工调整。">
        <SimpleTable
          rows={filteredLedger}
          columns={[
            { key: "time", header: "时间", cell: (row) => formatDateTime(row.createdAt) },
            { key: "account", header: "设备用户", cell: (row) => row.accountId },
            { key: "type", header: "类型", cell: (row) => row.entryType },
            { key: "currency", header: "币种", cell: (row) => row.currency },
            { key: "amount", header: "数量", cell: (row) => row.amount },
            { key: "source", header: "来源", cell: (row) => row.source },
            { key: "ref", header: "关联 ID", cell: (row) => row.referenceId ?? "-" },
          ]}
          emptyLabel="暂无钱包流水。"
        />
      </Panel>
    </Shell>
  );
}
