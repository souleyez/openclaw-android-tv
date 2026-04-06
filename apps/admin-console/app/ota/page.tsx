import {
  createClientConfigReleaseAction,
  createOtaReleaseAction,
  updateOtaReleaseStatusAction,
} from "@/app/ota/actions";
import { Panel } from "@/components/panel";
import { Shell } from "@/components/shell";
import { SimpleTable } from "@/components/simple-table";
import { StatsGrid } from "@/components/stats-grid";
import { StatusChip } from "@/components/status-chip";
import { fetchAdminOtaSnapshot, formatDateTime } from "@/lib/admin-api";

export const dynamic = "force-dynamic";

type OtaPageProps = {
  searchParams?: Promise<{ template?: string }>;
};

const policyLabels = {
  broadcast: "区域广播通知",
  idle_background: "后台闲时拉取",
  next_boot: "下次启动生效",
  idle_apply: "闲时立即应用",
} as const;

const rolloutStatusTone = (status: string) => {
  if (status === "completed") return "ok" as const;
  if (status === "rolling" || status === "paused") return "warn" as const;
  if (status === "rolled_back") return "danger" as const;
  return "default" as const;
};

export default async function OtaPage({ searchParams }: OtaPageProps) {
  const ota = await fetchAdminOtaSnapshot();
  const params = (await searchParams) ?? {};
  const selectedTemplate =
    ota.rolloutTemplates.find((item) => item.id === params.template) ??
    ota.rolloutTemplates[1] ??
    ota.rolloutTemplates[0];

  return (
    <Shell eyebrow="更新" title="客户端更新">
      <StatsGrid
        items={[
          {
            label: "滚动中的 OTA",
            value: ota.metrics.activeRollouts,
            hint: "仍在灰度中的安装包更新",
            tone: "accent",
          },
          {
            label: "暂停中的 OTA",
            value: ota.metrics.pausedRollouts,
            hint: "当前被暂缓的包级更新",
            tone: "warn",
          },
          {
            label: "版本分组",
            value: ota.metrics.versionGroups,
            hint: "当前客户端版本分布情况",
          },
          {
            label: "可升级设备",
            value: ota.metrics.otaReadyDevices,
            hint: "落在灰度范围内的预计设备数",
          },
        ]}
      />

      <div className="grid-2">
        <Panel title="发布 OTA 包更新" subtitle="用于安装包级更新。先发区域广播，再让客户端闲时下载。">
          <form action={createOtaReleaseAction} className="stack-form">
            <div className="toolbar-form__field">
              <label className="toolbar-form__label" htmlFor="ota-version-name">版本名称</label>
              <input className="toolbar-form__input" id="ota-version-name" name="versionName" placeholder="0.1.10-beta" />
            </div>
            <div className="toolbar-form__field">
              <label className="toolbar-form__label" htmlFor="ota-version-code">版本号</label>
              <input className="toolbar-form__input" id="ota-version-code" name="versionCode" placeholder="20" />
            </div>
            <div className="toolbar-form__field">
              <label className="toolbar-form__label" htmlFor="ota-release-channel">渠道</label>
              <select className="toolbar-form__select" defaultValue={selectedTemplate?.releaseChannel ?? "beta"} id="ota-release-channel" name="releaseChannel">
                <option value="stable">stable</option>
                <option value="beta">beta</option>
                <option value="internal">internal</option>
              </select>
            </div>
            <div className="toolbar-form__field">
              <label className="toolbar-form__label" htmlFor="ota-rollout-status">灰度状态</label>
              <select className="toolbar-form__select" defaultValue={selectedTemplate?.rolloutStatus ?? "draft"} id="ota-rollout-status" name="rolloutStatus">
                <option value="draft">draft</option>
                <option value="rolling">rolling</option>
                <option value="paused">paused</option>
                <option value="completed">completed</option>
              </select>
            </div>
            <div className="toolbar-form__field">
              <label className="toolbar-form__label" htmlFor="ota-target-scope">目标范围</label>
              <input className="toolbar-form__input" defaultValue={selectedTemplate?.targetScope ?? ""} id="ota-target-scope" name="targetScope" placeholder="radio-app / CN / Guangdong / android" />
            </div>
            <div className="toolbar-form__field">
              <label className="toolbar-form__label" htmlFor="ota-rollout-percent">灰度比例</label>
              <input className="toolbar-form__input" defaultValue={selectedTemplate?.rolloutPercent ?? 10} id="ota-rollout-percent" name="rolloutPercent" placeholder="10" />
            </div>
            <div className="toolbar-form__field">
              <label className="toolbar-form__label" htmlFor="ota-device-count">目标设备数</label>
              <input className="toolbar-form__input" defaultValue={selectedTemplate?.deviceCount ?? 12} id="ota-device-count" name="deviceCount" placeholder="12" />
            </div>
            <div className="toolbar-form__field">
              <label className="toolbar-form__label" htmlFor="ota-success-rate">预估成功率</label>
              <input className="toolbar-form__input" defaultValue={selectedTemplate?.installSuccessRate ?? 100} id="ota-success-rate" name="installSuccessRate" placeholder="100" />
            </div>
            <div className="toolbar-form__field">
              <label className="toolbar-form__label" htmlFor="ota-artifact-url">包地址</label>
              <input className="toolbar-form__input" id="ota-artifact-url" name="artifactUrl" placeholder="https://updates.sonance.app/radio-app/0.1.10/package.zip" />
            </div>
            <div className="toolbar-form__field">
              <label className="toolbar-form__label" htmlFor="ota-release-notes">广播文案</label>
              <textarea className="toolbar-form__textarea" id="ota-release-notes" name="releaseNotes" placeholder="广东用户可升级到 0.1.10。客户端会在闲时静默下载，并在下次启动时完成安装。" />
            </div>
            <div className="toolbar-form__actions">
              <button className="toolbar-form__button" type="submit">发布 OTA</button>
            </div>
          </form>
        </Panel>

        <Panel title="发布配置更新" subtitle="用于下发轻量 JSON 配置，客户端闲时拉取并应用。">
          <form action={createClientConfigReleaseAction} className="stack-form">
            <div className="toolbar-form__field">
              <label className="toolbar-form__label" htmlFor="config-version-name">配置版本名称</label>
              <input className="toolbar-form__input" id="config-version-name" name="versionName" placeholder="cn-music-boost-v2" />
            </div>
            <div className="toolbar-form__field">
              <label className="toolbar-form__label" htmlFor="config-version-code">配置版本号</label>
              <input className="toolbar-form__input" id="config-version-code" name="versionCode" placeholder="2" />
            </div>
            <div className="toolbar-form__field">
              <label className="toolbar-form__label" htmlFor="config-target-scope">目标范围</label>
              <input className="toolbar-form__input" id="config-target-scope" name="targetScope" placeholder="radio-app / CN / Guangdong / music" />
            </div>
            <div className="toolbar-form__field">
              <label className="toolbar-form__label" htmlFor="config-key">配置键</label>
              <input className="toolbar-form__input" defaultValue="radio-client-shell" id="config-key" name="configKey" />
            </div>
            <div className="toolbar-form__field">
              <label className="toolbar-form__label" htmlFor="config-rollout-status">灰度状态</label>
              <select className="toolbar-form__select" defaultValue="draft" id="config-rollout-status" name="rolloutStatus">
                <option value="draft">draft</option>
                <option value="rolling">rolling</option>
                <option value="paused">paused</option>
                <option value="completed">completed</option>
              </select>
            </div>
            <div className="toolbar-form__field">
              <label className="toolbar-form__label" htmlFor="config-apply-policy">应用策略</label>
              <select className="toolbar-form__select" defaultValue="idle_apply" id="config-apply-policy" name="applyPolicy">
                <option value="idle_apply">idle_apply</option>
                <option value="next_boot">next_boot</option>
              </select>
            </div>
            <div className="toolbar-form__field">
              <label className="toolbar-form__label" htmlFor="config-payload-json">配置 JSON</label>
              <textarea className="toolbar-form__textarea" defaultValue={`{\n  "preferredMode": "music",\n  "backgroundImageUrl": "https://images.unsplash.com/photo-1493225457124-a3eb161ffa5f?auto=format&fit=crop&w=1200&q=80"\n}`} id="config-payload-json" name="payloadJson" />
            </div>
            <div className="toolbar-form__field">
              <label className="toolbar-form__label" htmlFor="config-release-notes">广播文案</label>
              <textarea className="toolbar-form__textarea" id="config-release-notes" name="releaseNotes" placeholder="广东音乐偏好用户可在闲时收到新的轻量配置。" />
            </div>
            <div className="toolbar-form__actions">
              <button className="toolbar-form__button" type="submit">发布配置更新</button>
            </div>
          </form>
        </Panel>
      </div>

      <Panel title="执行策略" subtitle="客户端保持本地优先，更新只在后台和闲时进行。">
        <div className="grid-quick">
          <div className="quick-link">
            <div className="quick-link__title">{policyLabels.broadcast}</div>
            <div className="quick-link__caption">发布更新时，同时生成对应区域的系统广播通知。</div>
          </div>
          <div className="quick-link">
            <div className="quick-link__title">{policyLabels.idle_background}</div>
            <div className="quick-link__caption">不阻塞启动，不打断语音交互和广播播放。</div>
          </div>
          <div className="quick-link">
            <div className="quick-link__title">{policyLabels.next_boot}</div>
            <div className="quick-link__caption">OTA 包会先落盘排队，等下次启动窗口再安装。</div>
          </div>
          <div className="quick-link">
            <div className="quick-link__title">{policyLabels.idle_apply}</div>
            <div className="quick-link__caption">配置更新无需重装，可在客户端空闲时直接生效。</div>
          </div>
        </div>
      </Panel>

      <div className="grid-2">
        <Panel title="OTA 历史" subtitle="查看安装包更新的范围、状态和包地址。">
          <SimpleTable
            rows={ota.releases}
            columns={[
              { key: "version", header: "版本", cell: (row) => <span className="mono">{row.versionName}</span> },
              {
                key: "status",
                header: "状态",
                cell: (row) => <StatusChip label={row.rolloutStatus} tone={rolloutStatusTone(row.rolloutStatus)} />,
              },
              { key: "scope", header: "范围", cell: (row) => row.targetScope },
              { key: "artifact", header: "包地址", cell: (row) => row.artifactUrl ? <span className="mono">{row.artifactUrl}</span> : "-" },
              { key: "updated", header: "更新时间", cell: (row) => formatDateTime(row.updatedAt) },
            ]}
            emptyLabel="暂无 OTA 更新记录。"
          />
        </Panel>

        <Panel title="配置更新历史" subtitle="查看轻量配置版本、范围和应用策略。">
          <SimpleTable
            rows={ota.configReleases}
            columns={[
              { key: "version", header: "版本", cell: (row) => <span className="mono">{row.versionName}</span> },
              {
                key: "status",
                header: "状态",
                cell: (row) => <StatusChip label={row.rolloutStatus} tone={rolloutStatusTone(row.rolloutStatus)} />,
              },
              { key: "scope", header: "范围", cell: (row) => row.targetScope },
              { key: "configKey", header: "配置键", cell: (row) => row.configKey },
              { key: "policy", header: "策略", cell: (row) => `${policyLabels[row.fetchPolicy]} / ${policyLabels[row.applyPolicy]}` },
            ]}
            emptyLabel="暂无配置更新记录。"
          />
        </Panel>
      </div>

      <Panel title="快速调整状态" subtitle="无需重填整条记录，直接切换 OTA 灰度状态。">
        <SimpleTable
          rows={ota.releases}
          columns={[
            { key: "version", header: "版本", cell: (row) => row.versionName },
            { key: "channel", header: "渠道", cell: (row) => <StatusChip label={row.releaseChannel} /> },
            { key: "scope", header: "范围", cell: (row) => row.targetScope },
            {
              key: "action",
              header: "设置状态",
              cell: (row) => (
                <form action={updateOtaReleaseStatusAction} className="toolbar-form toolbar-form--inline">
                  <input type="hidden" name="releaseId" value={row.id} />
                  <input type="hidden" name="rolloutPercent" value={String(row.rolloutPercent)} />
                  <input type="hidden" name="deviceCount" value={String(row.deviceCount)} />
                  <input type="hidden" name="installSuccessRate" value={String(row.installSuccessRate)} />
                  <select className="toolbar-form__select" defaultValue={row.rolloutStatus} name="rolloutStatus">
                    <option value="draft">draft</option>
                    <option value="rolling">rolling</option>
                    <option value="paused">paused</option>
                    <option value="completed">completed</option>
                    <option value="rolled_back">rolled_back</option>
                  </select>
                  <button className="toolbar-form__button" type="submit">更新</button>
                </form>
              ),
            },
          ]}
          emptyLabel="暂无可调整的 OTA 更新。"
        />
      </Panel>
    </Shell>
  );
}
