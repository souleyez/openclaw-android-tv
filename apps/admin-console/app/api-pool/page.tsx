import {
  createApiPoolAccountAction,
  importApiPoolCredentialsAction,
  updateApiPoolAccountAction,
} from "@/app/api-pool/actions";
import { Panel } from "@/components/panel";
import { Shell } from "@/components/shell";
import { SimpleTable } from "@/components/simple-table";
import { StatsGrid } from "@/components/stats-grid";
import { StatusChip } from "@/components/status-chip";
import {
  fetchAdminApiPoolAccounts,
  fetchAdminSummary,
  formatDateTime,
} from "@/lib/admin-api";
import { formatGenericStatus } from "@/lib/admin-ui";

export const dynamic = "force-dynamic";

type ApiPoolPageProps = {
  searchParams?: Promise<{ q?: string; status?: string; page?: string }>;
};

export default async function ApiPoolPage({ searchParams }: ApiPoolPageProps) {
  const filters = (await searchParams) ?? {};
  const page = Math.max(1, Number.parseInt(filters.page ?? "1", 10) || 1);
  const [summary, accounts] = await Promise.all([
    fetchAdminSummary(),
    fetchAdminApiPoolAccounts({
      q: filters.q,
      status: filters.status,
      page,
      pageSize: 12,
    }),
  ]);

  const items = accounts.items;
  const activeAccounts = items.filter((item) => item.status === "active").length;
  const expiringAccounts = items.filter((item) => item.status === "expiring").length;
  const providers = new Set(items.map((item) => item.provider)).size;
  const totalApis = items.reduce((sum, item) => sum + (item.totalApis ?? 0), 0);
  const inUseApis = items.reduce((sum, item) => sum + (item.inUseApis ?? 0), 0);
  const idleApis = items.reduce((sum, item) => sum + (item.idleApis ?? 0), 0);
  const renewalRows = summary.apiPoolAccounts
    .slice()
    .sort(
      (a, b) =>
        new Date(a.expiresAt ?? 0).getTime() - new Date(b.expiresAt ?? 0).getTime(),
    )
    .slice(0, 5);

  return (
    <Shell eyebrow="模型池" title="API 池管理">
      <StatsGrid
        items={[
          {
            label: "池账户数",
            value: accounts.total,
            hint: `覆盖 ${providers} 个提供商`,
            tone: "accent",
          },
          {
            label: "正常账户",
            value: activeAccounts,
            hint: "当前仍可继续分配租约",
          },
          {
            label: "即将到期",
            value: expiringAccounts,
            hint: "建议尽快安排续期",
            tone: "warn",
          },
          {
            label: "API 总量",
            value: totalApis,
            hint: `使用中 ${inUseApis} / 空闲 ${idleApis}`,
          },
        ]}
      />

      <Panel title="租约规则" subtitle="声临默认采用短租直连策略，尽量减少服务器热路径压力。">
        <div className="grid-quick">
          <div className="quick-link">
            <div className="quick-link__title">短时租约</div>
            <div className="quick-link__caption">
              客户端拿到的是临时租约，不直接长期占用上游账户。
            </div>
          </div>
          <div className="quick-link">
            <div className="quick-link__title">单设备并发 1</div>
            <div className="quick-link__caption">
              同一设备同一时间只允许持有一份活跃租约。
            </div>
          </div>
          <div className="quick-link">
            <div className="quick-link__title">空闲自动回收</div>
            <div className="quick-link__caption">
              客户端长时间无交互会主动释放，过期租约也会自动失效。
            </div>
          </div>
        </div>
      </Panel>

      <div className="grid-2">
        <Panel title="账户列表" subtitle="服务端保存长期凭证，客户端只拿到短时租约。">
          <form className="toolbar-form" method="get">
            <div className="toolbar-form__field">
              <label className="toolbar-form__label" htmlFor="api-pool-q">
                搜索
              </label>
              <input
                className="toolbar-form__input"
                defaultValue={filters.q ?? ""}
                id="api-pool-q"
                name="q"
                placeholder="提供商 / 账户 / 套餐 / 备注"
              />
            </div>
            <div className="toolbar-form__field">
              <label className="toolbar-form__label" htmlFor="api-pool-status">
                状态
              </label>
              <select
                className="toolbar-form__select"
                defaultValue={filters.status ?? ""}
                id="api-pool-status"
                name="status"
              >
                <option value="">全部</option>
                <option value="active">正常</option>
                <option value="expiring">即将到期</option>
                <option value="expired">已过期</option>
                <option value="paused">已暂停</option>
              </select>
            </div>
            <div className="toolbar-form__actions">
              <button className="toolbar-form__button" type="submit">
                应用
              </button>
              <a className="toolbar-form__link" href="/api-pool">
                重置
              </a>
            </div>
          </form>

          <SimpleTable
            rows={items}
            columns={[
              { key: "provider", header: "提供商", cell: (row) => row.provider },
              { key: "label", header: "账户", cell: (row) => row.accountLabel },
              { key: "plan", header: "套餐", cell: (row) => row.planLabel },
              {
                key: "status",
                header: "状态",
                cell: (row) => (
                  <StatusChip
                    label={formatGenericStatus(row.status)}
                    tone={
                      row.status === "active"
                        ? "ok"
                        : row.status === "expiring"
                          ? "warn"
                          : "danger"
                    }
                  />
                ),
              },
              { key: "total", header: "总 API", cell: (row) => row.totalApis ?? 0 },
              { key: "inuse", header: "使用中", cell: (row) => row.inUseApis ?? 0 },
              { key: "idle", header: "空闲", cell: (row) => row.idleApis ?? 0 },
              {
                key: "expires",
                header: "到期时间",
                cell: (row) => formatDateTime(row.expiresAt),
              },
            ]}
            emptyLabel="当前筛选下没有 API 池账户。"
          />
        </Panel>

        <div className="stack">
          <Panel title="新增池账户" subtitle="先登记上游账户，再批量导入该账户下的 API 凭证。">
            <form action={createApiPoolAccountAction} className="stack-form">
              <div className="toolbar-form__field">
                <label className="toolbar-form__label" htmlFor="create-provider">
                  提供商
                </label>
                <input className="toolbar-form__input" id="create-provider" name="provider" placeholder="MiniMax" />
              </div>
              <div className="toolbar-form__field">
                <label className="toolbar-form__label" htmlFor="create-account-label">
                  账户标签
                </label>
                <input className="toolbar-form__input" id="create-account-label" name="accountLabel" placeholder="minimax-main-subscription" />
              </div>
              <div className="toolbar-form__field">
                <label className="toolbar-form__label" htmlFor="create-plan-label">
                  套餐标签
                </label>
                <input className="toolbar-form__input" id="create-plan-label" name="planLabel" placeholder="月订阅 / 年订阅 / 企业包" />
              </div>
              <div className="toolbar-form__field">
                <label className="toolbar-form__label" htmlFor="create-status">
                  状态
                </label>
                <select className="toolbar-form__select" defaultValue="active" id="create-status" name="status">
                  <option value="active">正常</option>
                  <option value="expiring">即将到期</option>
                  <option value="expired">已过期</option>
                  <option value="paused">已暂停</option>
                </select>
              </div>
              <div className="toolbar-form__field">
                <label className="toolbar-form__label" htmlFor="create-renews-at">
                  续期时间
                </label>
                <input className="toolbar-form__input" id="create-renews-at" name="renewsAt" placeholder="2026-04-01T00:00:00.000Z" />
              </div>
              <div className="toolbar-form__field">
                <label className="toolbar-form__label" htmlFor="create-expires-at">
                  到期时间
                </label>
                <input className="toolbar-form__input" id="create-expires-at" name="expiresAt" placeholder="2026-04-30T00:00:00.000Z" />
              </div>
              <div className="toolbar-form__field">
                <label className="toolbar-form__label" htmlFor="create-notes">
                  备注
                </label>
                <textarea className="toolbar-form__input" id="create-notes" name="notes" placeholder="人工备注" />
              </div>
              <div className="toolbar-form__actions">
                <button className="toolbar-form__button" type="submit">
                  创建账户
                </button>
              </div>
            </form>
          </Panel>

          <Panel title="批量导入 API" subtitle="一行一个 API key，导入后由服务端租约化下发。">
            <form action={importApiPoolCredentialsAction} className="stack-form">
              <div className="toolbar-form__field">
                <label className="toolbar-form__label" htmlFor="import-account-id">
                  池账户 ID
                </label>
                <input className="toolbar-form__input" id="import-account-id" name="accountId" placeholder="pool_minimax_..." />
              </div>
              <div className="toolbar-form__field">
                <label className="toolbar-form__label" htmlFor="import-provider">
                  提供商
                </label>
                <input className="toolbar-form__input" defaultValue="MiniMax" id="import-provider" name="provider" placeholder="MiniMax" />
              </div>
              <div className="toolbar-form__field">
                <label className="toolbar-form__label" htmlFor="import-base-url">
                  Base URL
                </label>
                <input className="toolbar-form__input" defaultValue="https://api.minimaxi.com/v1" id="import-base-url" name="baseUrl" placeholder="https://api.minimaxi.com/v1" />
              </div>
              <div className="toolbar-form__field">
                <label className="toolbar-form__label" htmlFor="import-model">
                  模型
                </label>
                <input className="toolbar-form__input" defaultValue="MiniMax-M2.5" id="import-model" name="model" placeholder="MiniMax-M2.5" />
              </div>
              <div className="toolbar-form__field">
                <label className="toolbar-form__label" htmlFor="import-raw-keys">
                  API 列表
                </label>
                <textarea className="toolbar-form__input" id="import-raw-keys" name="rawKeys" placeholder={"一行一个 API key\nsk-demo-1\nsk-demo-2"} />
              </div>
              <div className="toolbar-form__actions">
                <button className="toolbar-form__button" type="submit">
                  批量导入
                </button>
              </div>
            </form>
          </Panel>

          <Panel title="更新账户" subtitle="手工调整状态、续期时间、备注等信息。">
            <form action={updateApiPoolAccountAction} className="stack-form">
              <div className="toolbar-form__field">
                <label className="toolbar-form__label" htmlFor="update-id">
                  账户 ID
                </label>
                <input className="toolbar-form__input" id="update-id" name="id" placeholder="pool_minimax_001" />
              </div>
              <div className="toolbar-form__field">
                <label className="toolbar-form__label" htmlFor="update-provider">
                  提供商
                </label>
                <input className="toolbar-form__input" id="update-provider" name="provider" placeholder="MiniMax" />
              </div>
              <div className="toolbar-form__field">
                <label className="toolbar-form__label" htmlFor="update-account-label">
                  账户标签
                </label>
                <input className="toolbar-form__input" id="update-account-label" name="accountLabel" placeholder="minimax-main-subscription" />
              </div>
              <div className="toolbar-form__field">
                <label className="toolbar-form__label" htmlFor="update-plan-label">
                  套餐标签
                </label>
                <input className="toolbar-form__input" id="update-plan-label" name="planLabel" placeholder="月订阅 / 年订阅 / 企业包" />
              </div>
              <div className="toolbar-form__field">
                <label className="toolbar-form__label" htmlFor="update-status">
                  状态
                </label>
                <select className="toolbar-form__select" defaultValue="active" id="update-status" name="status">
                  <option value="active">正常</option>
                  <option value="expiring">即将到期</option>
                  <option value="expired">已过期</option>
                  <option value="paused">已暂停</option>
                </select>
              </div>
              <div className="toolbar-form__field">
                <label className="toolbar-form__label" htmlFor="update-renews-at">
                  续期时间
                </label>
                <input className="toolbar-form__input" id="update-renews-at" name="renewsAt" placeholder="2026-04-01T00:00:00.000Z" />
              </div>
              <div className="toolbar-form__field">
                <label className="toolbar-form__label" htmlFor="update-expires-at">
                  到期时间
                </label>
                <input className="toolbar-form__input" id="update-expires-at" name="expiresAt" placeholder="2026-04-30T00:00:00.000Z" />
              </div>
              <div className="toolbar-form__field">
                <label className="toolbar-form__label" htmlFor="update-notes">
                  备注
                </label>
                <textarea className="toolbar-form__input" id="update-notes" name="notes" placeholder="人工备注" />
              </div>
              <div className="toolbar-form__actions">
                <button className="toolbar-form__button" type="submit">
                  保存账户
                </button>
              </div>
            </form>
          </Panel>

          <Panel title="续期观察列表" subtitle="优先关注最早到期的上游账户。">
            <SimpleTable
              rows={renewalRows}
              columns={[
                { key: "provider", header: "提供商", cell: (row) => row.provider },
                { key: "account", header: "账户", cell: (row) => row.accountLabel },
                { key: "status", header: "状态", cell: (row) => formatGenericStatus(row.status) },
                { key: "expires", header: "到期时间", cell: (row) => formatDateTime(row.expiresAt) },
              ]}
              emptyLabel="暂无需要重点观察的账户。"
            />
          </Panel>
        </div>
      </div>
    </Shell>
  );
}
