import { addManualRadioSourceAction, addRadioSourceFromSearchAction } from "@/app/radio/actions";
import { Panel } from "@/components/panel";
import { Shell } from "@/components/shell";
import { SimpleTable } from "@/components/simple-table";
import { StatsGrid } from "@/components/stats-grid";
import { StatusChip } from "@/components/status-chip";
import {
  fetchAdminRadioSnapshot,
  fetchAdminRadioSourceSearch,
  formatDateTime,
} from "@/lib/admin-api";

export const dynamic = "force-dynamic";

type RadioPageProps = {
  searchParams?: Promise<{
    q?: string;
    countryCode?: string;
    limit?: string;
  }>;
};

export default async function RadioPage({ searchParams }: RadioPageProps) {
  const filters = (await searchParams) ?? {};
  const limit = Math.max(1, Math.min(24, Number.parseInt(filters.limit ?? "10", 10) || 10));
  const [radio, search] = await Promise.all([
    fetchAdminRadioSnapshot(),
    fetchAdminRadioSourceSearch({
      q: filters.q,
      countryCode: filters.countryCode,
      limit,
    }),
  ]);

  return (
    <Shell eyebrow="Radio" title="节目源管理">
      <StatsGrid
        items={[
          {
            label: "本地节目源",
            value: radio.metrics.totalStations,
            hint: `覆盖 ${radio.metrics.activeCountries} 个国家`,
            tone: "accent",
          },
          {
            label: "导入电台",
            value: radio.metrics.importedStations,
            hint: "来自公开目录或后台手工扩充",
          },
          {
            label: "健康电台",
            value: radio.metrics.healthyStations ?? 0,
            hint: `降级 ${radio.metrics.degradedStations ?? 0} 个`,
          },
          {
            label: "广播条目",
            value: radio.recentBroadcasts.length,
            hint: `队列 ${radio.queue.running}/${radio.queue.concurrency}`,
            tone: "warn",
          },
        ]}
      />

      <div className="grid-2">
        <Panel title="自动搜索节目源" subtitle="按国家和关键词搜索公开电台，再一键加入本地目录。">
          <form className="toolbar-form" method="get">
            <div className="toolbar-form__field">
              <label className="toolbar-form__label" htmlFor="radio-country">国家码</label>
              <input className="toolbar-form__input" defaultValue={filters.countryCode ?? ""} id="radio-country" name="countryCode" placeholder="CN / JP / US" />
            </div>
            <div className="toolbar-form__field">
              <label className="toolbar-form__label" htmlFor="radio-q">关键词</label>
              <input className="toolbar-form__input" defaultValue={filters.q ?? ""} id="radio-q" name="q" placeholder="music / news / shanghai" />
            </div>
            <div className="toolbar-form__field">
              <label className="toolbar-form__label" htmlFor="radio-limit">数量</label>
              <input className="toolbar-form__input" defaultValue={String(limit)} id="radio-limit" name="limit" type="number" min="1" max="24" />
            </div>
            <div className="toolbar-form__actions">
              <button className="toolbar-form__button" type="submit">搜索</button>
              <a className="toolbar-form__link" href="/radio">重置</a>
            </div>
          </form>

          <SimpleTable
            rows={search.items}
            columns={[
              {
                key: "name",
                header: "节目源",
                cell: (row) => (
                  <div>
                    <strong>{row.name}</strong>
                    <div className="hint">
                      {row.countryCode}
                      {row.regionCode ? ` / ${row.regionCode}` : ""}
                      {row.city ? ` / ${row.city}` : ""}
                    </div>
                  </div>
                ),
              },
              {
                key: "profile",
                header: "语言 / 类型",
                cell: (row) => `${row.language || "-"} / ${row.genre || "-"}`,
              },
              {
                key: "status",
                header: "是否已存在",
                cell: (row) =>
                  row.alreadyExists ? (
                    <div>
                      <StatusChip label="active" tone="warn" />
                      <div className="hint">{row.existingStationName ?? "已在目录中"}</div>
                    </div>
                  ) : (
                    <StatusChip label="ready" tone="ok" />
                  ),
              },
              {
                key: "action",
                header: "加入",
                cell: (row) =>
                  row.alreadyExists ? (
                    <span className="hint">已存在</span>
                  ) : (
                    <form action={addRadioSourceFromSearchAction}>
                      <input name="externalId" type="hidden" value={row.externalId ?? ""} />
                      <input name="name" type="hidden" value={row.name} />
                      <input name="countryCode" type="hidden" value={row.countryCode} />
                      <input name="regionCode" type="hidden" value={row.regionCode ?? ""} />
                      <input name="city" type="hidden" value={row.city} />
                      <input name="language" type="hidden" value={row.language} />
                      <input name="genre" type="hidden" value={row.genre} />
                      <input name="streamUrl" type="hidden" value={row.streamUrl} />
                      <input name="homepageUrl" type="hidden" value={row.homepageUrl ?? ""} />
                      <input name="logoUrl" type="hidden" value={row.logoUrl ?? ""} />
                      <button className="toolbar-form__button" type="submit">加入节目源</button>
                    </form>
                  ),
              },
            ]}
            emptyLabel="先输入国家码或关键词，再搜索公开节目源。"
          />
        </Panel>

        <Panel title="手工加入节目源" subtitle="适合补自定义流地址、内部备用流或目录里搜不到的台。">
          <form action={addManualRadioSourceAction} className="stack-form">
            <div className="toolbar-form__field">
              <label className="toolbar-form__label" htmlFor="manual-name">名称</label>
              <input className="toolbar-form__input" id="manual-name" name="name" placeholder="上海流行音乐广播" />
            </div>
            <div className="toolbar-form__field">
              <label className="toolbar-form__label" htmlFor="manual-country">国家码</label>
              <input className="toolbar-form__input" id="manual-country" name="countryCode" placeholder="CN" />
            </div>
            <div className="toolbar-form__field">
              <label className="toolbar-form__label" htmlFor="manual-region">地区</label>
              <input className="toolbar-form__input" id="manual-region" name="regionCode" placeholder="SHANGHAI" />
            </div>
            <div className="toolbar-form__field">
              <label className="toolbar-form__label" htmlFor="manual-city">城市</label>
              <input className="toolbar-form__input" id="manual-city" name="city" placeholder="Shanghai" />
            </div>
            <div className="toolbar-form__field">
              <label className="toolbar-form__label" htmlFor="manual-language">语言</label>
              <input className="toolbar-form__input" id="manual-language" name="language" placeholder="中文 / 粤语 / 日本语" />
            </div>
            <div className="toolbar-form__field">
              <label className="toolbar-form__label" htmlFor="manual-genre">类型</label>
              <input className="toolbar-form__input" id="manual-genre" name="genre" placeholder="Music / News / Talk" />
            </div>
            <div className="toolbar-form__field">
              <label className="toolbar-form__label" htmlFor="manual-band">标记</label>
              <input className="toolbar-form__input" defaultValue="WEB" id="manual-band" name="bandLabel" placeholder="WEB" />
            </div>
            <div className="toolbar-form__field">
              <label className="toolbar-form__label" htmlFor="manual-stream">流地址</label>
              <input className="toolbar-form__input" id="manual-stream" name="streamUrl" placeholder="https://example.com/live.mp3" />
            </div>
            <div className="toolbar-form__field">
              <label className="toolbar-form__label" htmlFor="manual-homepage">主页</label>
              <input className="toolbar-form__input" id="manual-homepage" name="homepageUrl" placeholder="https://station.example.com" />
            </div>
            <div className="toolbar-form__field">
              <label className="toolbar-form__label" htmlFor="manual-logo">Logo</label>
              <input className="toolbar-form__input" id="manual-logo" name="logoUrl" placeholder="https://station.example.com/logo.png" />
            </div>
            <div className="toolbar-form__field">
              <label className="toolbar-form__label" htmlFor="manual-notes">备注</label>
              <input className="toolbar-form__input" id="manual-notes" name="legalNotes" placeholder="Manual source added from admin console" />
            </div>
            <div className="toolbar-form__actions">
              <button className="toolbar-form__button" type="submit">手工加入</button>
            </div>
          </form>
        </Panel>
      </div>

      <div className="grid-2">
        <Panel title="当前节目源目录" subtitle="展示当前本地可用节目源，便于快速确认是否已加入。">
          <SimpleTable
            rows={radio.stations.slice(0, 40)}
            columns={[
              {
                key: "name",
                header: "电台",
                cell: (row) => (
                  <div>
                    <strong>{row.name}</strong>
                    <div className="hint">
                      {row.country}
                      {row.region ? ` / ${row.region}` : ""}
                      {row.city ? ` / ${row.city}` : ""}
                    </div>
                  </div>
                ),
              },
              { key: "meta", header: "语言 / 类型", cell: (row) => `${row.language} / ${row.genre}` },
              {
                key: "health",
                header: "健康",
                cell: (row) => (
                  <StatusChip
                    label={row.lastHealthStatus ?? "unknown"}
                    tone={
                      row.lastHealthStatus === "healthy"
                        ? "ok"
                        : row.lastHealthStatus === "degraded"
                          ? "warn"
                          : "default"
                    }
                  />
                ),
              },
              { key: "checked", header: "最近检查", cell: (row) => formatDateTime(row.lastCheckedAt) },
            ]}
            emptyLabel="暂无节目源。"
          />
        </Panel>

        <Panel title="最近广播列表" subtitle="方便值班时确认 AI 广播和用户广播是否正常落地。">
          <SimpleTable
            rows={radio.recentBroadcasts}
            columns={[
              { key: "title", header: "标题", cell: (row) => row.title },
              {
                key: "sourceKind",
                header: "来源",
                cell: (row) => (
                  <StatusChip
                    label={row.sourceKind}
                    tone={row.sourceKind === "ai" ? "ok" : "default"}
                  />
                ),
              },
              { key: "station", header: "电台", cell: (row) => row.stationName ?? "-" },
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
              { key: "createdAt", header: "创建时间", cell: (row) => formatDateTime(row.createdAt) },
            ]}
            emptyLabel="暂无广播条目"
          />
        </Panel>
      </div>
    </Shell>
  );
}
