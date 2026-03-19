import { Panel } from "@/components/panel";
import { Shell } from "@/components/shell";
import { SimpleTable } from "@/components/simple-table";
import { StatsGrid } from "@/components/stats-grid";
import { StatusChip } from "@/components/status-chip";
import {
  fetchAdminDeviceUsers,
  fetchAdminSummary,
  formatDateTime,
  formatDaysLeft,
  formatGenericStatus,
} from "@/lib/admin-api";

export const dynamic = "force-dynamic";

type DeviceUsersPageProps = {
  searchParams?: Promise<{
    q?: string;
    status?: string;
    page?: string;
  }>;
};

export default async function DeviceUsersPage({ searchParams }: DeviceUsersPageProps) {
  const filters = (await searchParams) ?? {};
  const page = Math.max(1, Number.parseInt(filters.page ?? "1", 10) || 1);
  const [summary, users] = await Promise.all([
    fetchAdminSummary(),
    fetchAdminDeviceUsers({
      q: filters.q,
      status: filters.status,
      page,
      pageSize: 12,
    }),
  ]);

  const items = users.items;
  const activeUsers = items.filter((item) => item.status === "active").length;
  const expiringSoon = items.filter((item) => {
    if (!item.entitlementExpiresAt) {
      return false;
    }

    const diffDays = Math.ceil(
      (new Date(item.entitlementExpiresAt).getTime() - Date.now()) /
        (1000 * 60 * 60 * 24),
    );
    return diffDays <= 14;
  }).length;

  return (
    <Shell eyebrow="身份" title="设备用户与权益">
      <StatsGrid
        items={[
          {
            label: "设备用户",
            value: users.total,
            hint: "出厂写入的轻账户身份",
            tone: "accent",
          },
          {
            label: "有效权益",
            value: activeUsers,
            hint: "当前可正常使用",
          },
          {
            label: "即将到期",
            value: expiringSoon,
            hint: "未来 14 天需要续期或恢复",
            tone: "warn",
          },
          {
            label: "继承记录",
            value: summary.counts.transfers,
            hint: "设备之间的权益转移次数",
          },
        ]}
      />

      <div className="grid-2">
        <Panel title="权益登记表" subtitle="设备绑定用户与权益状态的主操作表。">
          <form className="toolbar-form" method="get">
            <div className="toolbar-form__field">
              <label className="toolbar-form__label" htmlFor="device-users-q">
                搜索
              </label>
              <input
                className="toolbar-form__input"
                defaultValue={filters.q ?? ""}
                id="device-users-q"
                name="q"
                placeholder="设备用户 / 显示名 / 套餐 / 恢复提示"
              />
            </div>
            <div className="toolbar-form__field">
              <label className="toolbar-form__label" htmlFor="device-users-status">
                状态
              </label>
              <select
                className="toolbar-form__select"
                defaultValue={filters.status ?? ""}
                id="device-users-status"
                name="status"
              >
                <option value="">全部</option>
                <option value="active">正常</option>
                <option value="disabled">停用</option>
              </select>
            </div>
            <div className="toolbar-form__actions">
              <button className="toolbar-form__button" type="submit">
                应用
              </button>
              <a className="toolbar-form__link" href="/device-users">
                重置
              </a>
            </div>
          </form>

          <SimpleTable
            rows={items}
            columns={[
              { key: "id", header: "设备用户", cell: (row) => <span className="mono">{row.id}</span> },
              { key: "displayName", header: "显示名", cell: (row) => row.displayName },
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
              { key: "expiry", header: "到期时间", cell: (row) => formatDateTime(row.entitlementExpiresAt) },
              { key: "days", header: "剩余天数", cell: (row) => formatDaysLeft(row.entitlementExpiresAt) },
            ]}
            emptyLabel="当前筛选下没有设备用户。"
          />

          <div className="pagination-bar">
            <span>
              显示 {items.length} / {users.total} 条 · 第 {users.page} 页
            </span>
            <div className="pagination-bar__actions">
              <a
                className={`pagination-bar__link${users.page <= 1 ? " pagination-bar__link--disabled" : ""}`}
                href={`/device-users?q=${encodeURIComponent(filters.q ?? "")}&status=${encodeURIComponent(filters.status ?? "")}&page=${Math.max(1, users.page - 1)}`}
              >
                上一页
              </a>
              <a
                className={`pagination-bar__link${users.page * users.pageSize >= users.total ? " pagination-bar__link--disabled" : ""}`}
                href={`/device-users?q=${encodeURIComponent(filters.q ?? "")}&status=${encodeURIComponent(filters.status ?? "")}&page=${users.page + 1}`}
              >
                下一页
              </a>
            </div>
          </div>
        </Panel>

        <Panel title="继承与恢复说明" subtitle="当前轻账户规则。">
          <div className="meta-list">
            <div className="meta-row">
              <span className="meta-row__label">身份主键</span>
              <strong>device_user_id</strong>
            </div>
            <div className="meta-row">
              <span className="meta-row__label">找回依据</span>
              <strong>最后一次支付凭证</strong>
            </div>
            <div className="meta-row">
              <span className="meta-row__label">继承规则</span>
              <strong>新设备继承后，旧设备失去权益</strong>
            </div>
            <div className="meta-row">
              <span className="meta-row__label">共享原则</span>
              <strong>不同 device_user_id 默认不共享权益</strong>
            </div>
          </div>
        </Panel>
      </div>
    </Shell>
  );
}
