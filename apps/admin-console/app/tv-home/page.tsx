import { Panel } from "@/components/panel";
import { Shell } from "@/components/shell";
import { SimpleTable } from "@/components/simple-table";
import { StatusChip } from "@/components/status-chip";
import { fetchAdminTvHomeSnapshot, formatDateTime } from "@/lib/admin-api";
import { saveTvHomeConfigAction } from "./actions";

export const dynamic = "force-dynamic";

export default async function TvHomePage() {
  const snapshot = await fetchAdminTvHomeSnapshot();
  const defaultApps = snapshot.appCatalog
    .slice(0, 5)
    .map((item) => item.appId)
    .join(", ");

  return (
    <Shell eyebrow="TV Home" title="首页投放配置">
      <div className="grid-2">
        <Panel
          title="区域配置"
          subtitle="按国家和区域维护首页背景图 URL 与可展示 appId 列表。第一版只下发 URL，不做文件托管。"
        >
          <form action={saveTvHomeConfigAction} className="stack-form">
            <div className="toolbar-form">
              <label className="toolbar-form__field">
                <span className="toolbar-form__label">配置 ID</span>
                <input className="toolbar-form__input" name="id" placeholder="留空则自动生成" />
              </label>
              <label className="toolbar-form__field">
                <span className="toolbar-form__label">国家代码</span>
                <input className="toolbar-form__input" name="countryCode" defaultValue="GLOBAL" placeholder="US / JP / GLOBAL" />
              </label>
              <label className="toolbar-form__field">
                <span className="toolbar-form__label">区域代码</span>
                <input className="toolbar-form__input" name="regionCode" placeholder="CA / LATAM / GLOBAL" />
              </label>
              <label className="toolbar-form__field">
                <span className="toolbar-form__label">状态</span>
                <select className="toolbar-form__select" name="status" defaultValue="active">
                  <option value="active">active</option>
                  <option value="draft">draft</option>
                </select>
              </label>
            </div>

            <label>
              <span className="toolbar-form__label">背景图 URL</span>
              <input className="toolbar-form__input" name="backgroundImageUrl" placeholder="https://cdn.example.com/tv-home/us-hero.jpg" />
            </label>

            <label>
              <span className="toolbar-form__label">展示 APP 列表</span>
              <textarea className="toolbar-form__input" name="featuredAppIds" rows={4} defaultValue={defaultApps} placeholder="youtube, netflix, prime_video, disney_plus, plex" />
            </label>

            <div className="toolbar-form__actions">
              <button className="toolbar-form__button" type="submit">
                保存配置
              </button>
            </div>
          </form>
        </Panel>

        <Panel title="支持目录" subtitle="full 表示链路完整；basic 表示仅支持打开应用和基础遥控。">
          <SimpleTable
            rows={snapshot.appCatalog}
            columns={[
              { key: "name", header: "应用", cell: (row) => row.displayName },
              {
                key: "id",
                header: "appId",
                cell: (row) => <span className="mono">{row.appId}</span>,
              },
              {
                key: "tier",
                header: "支持级别",
                cell: (row) => (
                  <StatusChip
                    label={row.supportTier}
                    tone={row.supportTier === "full" ? "ok" : "warn"}
                  />
                ),
              },
              {
                key: "actions",
                header: "能力",
                cell: (row) => row.supportedActions.join(", "),
              },
            ]}
            emptyLabel="暂无可投放应用目录。"
          />
        </Panel>
      </div>

      <Panel title="已保存配置" subtitle="客户端按国家和区域精确命中，找不到时回退到国家默认，再回退到 GLOBAL。">
        <SimpleTable
          rows={snapshot.configs}
          columns={[
            {
              key: "scope",
              header: "范围",
              cell: (row) => `${row.countryCode}/${row.regionCode ?? "GLOBAL"}`,
            },
            { key: "status", header: "状态", cell: (row) => row.status },
            { key: "version", header: "版本", cell: (row) => row.version },
            {
              key: "apps",
              header: "APP",
              cell: (row) => row.featuredAppIds.join(", "),
            },
            {
              key: "bg",
              header: "背景图",
              cell: (row) => (row.backgroundImageUrl ? "已配置" : "-"),
            },
            {
              key: "updated",
              header: "更新时间",
              cell: (row) => formatDateTime(row.updatedAt),
            },
          ]}
          emptyLabel="暂无首页投放配置。"
        />
      </Panel>
    </Shell>
  );
}
