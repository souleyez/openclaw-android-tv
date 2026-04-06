import Link from "next/link";
import { Panel } from "@/components/panel";
import { Shell } from "@/components/shell";
import { SimpleTable } from "@/components/simple-table";
import { StatsGrid } from "@/components/stats-grid";
import { fetchAdminSearch, formatDateTime } from "@/lib/admin-api";

export const dynamic = "force-dynamic";

type SearchPageProps = {
  searchParams?: Promise<{ q?: string }>;
};

export default async function SearchPage({ searchParams }: SearchPageProps) {
  const filters = (await searchParams) ?? {};
  const result = await fetchAdminSearch(filters.q ?? "");
  const hasQuery = result.query.length > 0;
  const query = result.query;
  const totalHits =
    result.deviceUsers.length +
    result.orders.length +
    result.logs.length +
    result.transfers.length;

  function highlightText(value?: string) {
    if (!value) return "-";
    if (!query) return value;

    const lowerValue = value.toLowerCase();
    const lowerQuery = query.toLowerCase();
    const parts: Array<{ text: string; match: boolean }> = [];
    let cursor = 0;

    while (cursor < value.length) {
      const matchIndex = lowerValue.indexOf(lowerQuery, cursor);
      if (matchIndex === -1) {
        parts.push({ text: value.slice(cursor), match: false });
        break;
      }
      if (matchIndex > cursor) {
        parts.push({ text: value.slice(cursor, matchIndex), match: false });
      }
      parts.push({ text: value.slice(matchIndex, matchIndex + query.length), match: true });
      cursor = matchIndex + query.length;
    }

    return parts.map((part, index) =>
      part.match ? (
        <mark key={`${value}-${index}`} className="search-highlight">
          {part.text}
        </mark>
      ) : (
        <span key={`${value}-${index}`}>{part.text}</span>
      ),
    );
  }

  return (
    <Shell eyebrow="搜索" title="全局搜索">
      <StatsGrid
        items={[
          {
            label: "搜索词",
            value: hasQuery ? result.query : "-",
            hint: "设备用户 / txHash / 订单号",
            tone: "accent",
          },
          {
            label: "命中总数",
            value: totalHits,
            hint: "跨模块搜索结果",
          },
          {
            label: "订单命中",
            value: result.orders.length,
            hint: "支付与结算线索",
          },
          {
            label: "日志命中",
            value: result.logs.length,
            hint: "助手与策略证据",
            tone: "warn",
          },
        ]}
      />

      <Panel title="统一检索" subtitle="一次查询同时扫描设备用户、订单、日志和继承记录。">
        <form className="toolbar-form" method="get">
          <div className="toolbar-form__field toolbar-form__field--grow">
            <label className="toolbar-form__label" htmlFor="search-q">
              搜索
            </label>
            <input
              className="toolbar-form__input"
              defaultValue={filters.q ?? ""}
              id="search-q"
              name="q"
              placeholder="输入 device_user_id、orderId、txHash、恢复提示等关键词"
            />
          </div>
          <div className="toolbar-form__actions">
            <button className="toolbar-form__button" type="submit">
              搜索
            </button>
            <a className="toolbar-form__link" href="/search">
              重置
            </a>
          </div>
        </form>
      </Panel>

      <div className="grid-2">
        <Panel title="设备用户" subtitle="账户与权益层的命中结果。">
          <SimpleTable
            rows={result.deviceUsers}
            columns={[
              { key: "id", header: "设备用户", cell: (row) => highlightText(row.id) },
              { key: "name", header: "显示名", cell: (row) => highlightText(row.displayName) },
              { key: "plan", header: "套餐", cell: (row) => highlightText(row.planCode) },
              {
                key: "jump",
                header: "跳转",
                cell: (row) => (
                  <Link className="result-link" href={`/device-users?q=${encodeURIComponent(row.id)}`}>
                    打开设备用户
                  </Link>
                ),
              },
            ]}
            emptyLabel="暂无设备用户命中。"
          />
        </Panel>

        <Panel title="订单" subtitle="支付、结算与恢复凭证相关结果。">
          <SimpleTable
            rows={result.orders}
            columns={[
              { key: "id", header: "订单号", cell: (row) => highlightText(row.id) },
              { key: "account", header: "设备用户", cell: (row) => highlightText(row.accountId) },
              { key: "tx", header: "Tx Hash", cell: (row) => highlightText(row.txHash) },
              {
                key: "jump",
                header: "跳转",
                cell: (row) => (
                  <Link className="result-link" href={`/orders?q=${encodeURIComponent(row.id)}`}>
                    打开订单
                  </Link>
                ),
              },
            ]}
            emptyLabel="暂无订单命中。"
          />
        </Panel>
      </div>

      <div className="grid-2">
        <Panel title="日志" subtitle="模型、控制、回退和拦截证据。">
          <SimpleTable
            rows={result.logs}
            columns={[
              { key: "time", header: "时间", cell: (row) => formatDateTime(row.createdAt) },
              { key: "route", header: "路由", cell: (row) => highlightText(row.route) },
              { key: "input", header: "用户输入", cell: (row) => highlightText(row.userText) },
              {
                key: "jump",
                header: "跳转",
                cell: (row) => (
                  <Link className="result-link" href={`/logs?q=${encodeURIComponent(row.id)}`}>
                    打开日志
                  </Link>
                ),
              },
            ]}
            emptyLabel="暂无日志命中。"
          />
        </Panel>

        <Panel title="继承记录" subtitle="设备权益迁移与恢复记录。">
          <SimpleTable
            rows={result.transfers}
            columns={[
              { key: "id", header: "记录 ID", cell: (row) => highlightText(row.id) },
              { key: "from", header: "来源用户", cell: (row) => highlightText(row.fromAccountId) },
              { key: "to", header: "目标用户", cell: (row) => highlightText(row.toAccountId) },
              { key: "tx", header: "支付凭证", cell: (row) => highlightText(row.paymentProofTxHash) },
            ]}
            emptyLabel="暂无继承记录命中。"
          />
        </Panel>
      </div>
    </Shell>
  );
}
