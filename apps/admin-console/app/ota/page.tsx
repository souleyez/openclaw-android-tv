import { createOtaReleaseAction, updateOtaReleaseStatusAction } from "@/app/ota/actions";
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

const strategyLabels = {
  broadcast: "广播通知",
  idle_background: "空闲慢速下载",
  next_boot: "关机或下次开机启用",
  lazy: "延迟回执"
} as const;

export default async function OtaPage({ searchParams }: OtaPageProps) {
  const ota = await fetchAdminOtaSnapshot();
  const params = (await searchParams) ?? {};
  const selectedTemplate =
    ota.rolloutTemplates.find((item) => item.id === params.template) ??
    ota.rolloutTemplates[1] ??
    ota.rolloutTemplates[0];

  return (
    <Shell eyebrow="发布" title="OTA 与版本">
      <StatsGrid
        items={[
          { label: "进行中发布", value: ota.metrics.activeRollouts, hint: "当前仍在灰度推送的版本", tone: "accent" },
          { label: "暂停发布", value: ota.metrics.pausedRollouts, hint: "已暂停等待排查", tone: "warn" },
          { label: "版本分组", value: ota.metrics.versionGroups, hint: "当前设备版本分布档位" },
          { label: "可 OTA 设备", value: ota.metrics.otaReadyDevices, hint: "纳入当前推送范围的设备" }
        ]}
      />

      <div className="grid-2">
        <Panel title="发布波次" subtitle="当前版本、灰度比例和安装成功率。">
          <SimpleTable
            rows={ota.releases}
            columns={[
              { key: "version", header: "版本", cell: (row) => <span className="mono">{row.versionName}</span> },
              { key: "code", header: "版本号", cell: (row) => row.versionCode },
              { key: "channel", header: "渠道", cell: (row) => row.releaseChannel },
              {
                key: "status",
                header: "状态",
                cell: (row) => (
                  <StatusChip
                    label={row.rolloutStatus}
                    tone={
                      row.rolloutStatus === "rolling"
                        ? "warn"
                        : row.rolloutStatus === "completed"
                          ? "ok"
                          : row.rolloutStatus === "rolled_back"
                            ? "danger"
                            : "default"
                    }
                  />
                )
              },
              { key: "scope", header: "目标范围", cell: (row) => row.targetScope },
              { key: "percent", header: "灰度比例", cell: (row) => `${row.rolloutPercent}%` },
              { key: "success", header: "成功率", cell: (row) => `${row.installSuccessRate}%` }
            ]}
            emptyLabel="暂无 OTA 发布记录。"
          />
        </Panel>

        <div className="stack">
          <Panel title="推送策略" subtitle="当前默认策略：广播通知、空闲下载、关机/下次开机启用、延迟回执。">
            <div className="grid-quick">
              <div className="quick-link">
                <div className="quick-link__title">{strategyLabels.broadcast}</div>
                <div className="quick-link__caption">前端只要收到版本广播，就记下更新信息，不急着打服务器热链路。</div>
              </div>
              <div className="quick-link">
                <div className="quick-link__title">{strategyLabels.idle_background}</div>
                <div className="quick-link__caption">设备空闲时再慢速下载，避免影响语音和本地控制。</div>
              </div>
              <div className="quick-link">
                <div className="quick-link__title">{strategyLabels.next_boot}</div>
                <div className="quick-link__caption">下载完成先暂存，等关机窗口或下次开机再启用新版本。</div>
              </div>
              <div className="quick-link">
                <div className="quick-link__title">{strategyLabels.lazy}</div>
                <div className="quick-link__caption">更新成功先本地排队，空闲时再回执，减少服务端瞬时压力。</div>
              </div>
            </div>
          </Panel>

          <Panel title="发布模板" subtitle="直接套用常见发布环，减少手填参数。">
            <div className="grid-quick">
              {ota.rolloutTemplates.map((template) => (
                <a className="quick-link" href={`/ota?template=${encodeURIComponent(template.id)}`} key={template.id}>
                  <div className="quick-link__title">{template.label}</div>
                  <div className="quick-link__meta">{template.releaseChannel} | {template.rolloutPercent}% 灰度</div>
                  <div className="quick-link__caption">{template.targetScope}</div>
                </a>
              ))}
            </div>
          </Panel>

          <Panel title="创建发布" subtitle="登记新版本并放入指定灰度环。">
            <form action={createOtaReleaseAction} className="stack-form">
              <div className="toolbar-form__field"><label className="toolbar-form__label" htmlFor="ota-version-name">版本名</label><input className="toolbar-form__input" id="ota-version-name" name="versionName" placeholder="0.1.10-beta" /></div>
              <div className="toolbar-form__field"><label className="toolbar-form__label" htmlFor="ota-version-code">版本号</label><input className="toolbar-form__input" id="ota-version-code" name="versionCode" placeholder="20" /></div>
              <div className="toolbar-form__field"><label className="toolbar-form__label" htmlFor="ota-release-channel">发布渠道</label><select className="toolbar-form__select" defaultValue={selectedTemplate?.releaseChannel ?? "beta"} id="ota-release-channel" name="releaseChannel"><option value="stable">stable</option><option value="beta">beta</option><option value="internal">internal</option></select></div>
              <div className="toolbar-form__field"><label className="toolbar-form__label" htmlFor="ota-rollout-status">初始状态</label><select className="toolbar-form__select" defaultValue={selectedTemplate?.rolloutStatus ?? "draft"} id="ota-rollout-status" name="rolloutStatus"><option value="draft">draft</option><option value="rolling">rolling</option><option value="paused">paused</option><option value="completed">completed</option></select></div>
              <div className="toolbar-form__field"><label className="toolbar-form__label" htmlFor="ota-target-scope">目标范围</label><input className="toolbar-form__input" defaultValue={selectedTemplate?.targetScope ?? ""} id="ota-target-scope" name="targetScope" placeholder="Android TV / closed beta / SEA" /></div>
              <div className="toolbar-form__field"><label className="toolbar-form__label" htmlFor="ota-rollout-percent">灰度比例</label><input className="toolbar-form__input" defaultValue={selectedTemplate?.rolloutPercent ?? 10} id="ota-rollout-percent" name="rolloutPercent" placeholder="10" /></div>
              <div className="toolbar-form__field"><label className="toolbar-form__label" htmlFor="ota-device-count">目标设备数</label><input className="toolbar-form__input" defaultValue={selectedTemplate?.deviceCount ?? 12} id="ota-device-count" name="deviceCount" placeholder="12" /></div>
              <div className="toolbar-form__field"><label className="toolbar-form__label" htmlFor="ota-success-rate">预计成功率</label><input className="toolbar-form__input" defaultValue={selectedTemplate?.installSuccessRate ?? 100} id="ota-success-rate" name="installSuccessRate" placeholder="100" /></div>
              <div className="toolbar-form__actions"><button className="toolbar-form__button" type="submit">创建发布</button></div>
            </form>
          </Panel>
        </div>
      </div>

      <Panel title="最近版本记录" subtitle="策略和波次会一起留档。">
        <SimpleTable
          rows={ota.releases}
          columns={[
            { key: "version", header: "版本", cell: (row) => row.versionName },
            { key: "created", header: "创建时间", cell: (row) => formatDateTime(row.createdAt) },
            { key: "updated", header: "更新时间", cell: (row) => formatDateTime(row.updatedAt) },
            { key: "channel", header: "渠道", cell: (row) => row.releaseChannel },
            { key: "devices", header: "目标设备", cell: (row) => row.deviceCount },
            {
              key: "policy",
              header: "执行策略",
              cell: (row) =>
                `${strategyLabels[row.notificationMode]} / ${strategyLabels[row.downloadPolicy]} / ${strategyLabels[row.installPolicy]} / ${strategyLabels[row.reportPolicy]} (${row.reportDelayMinutes} 分钟)`
            }
          ]}
          emptyLabel="暂无最近发布记录。"
        />
      </Panel>

      <Panel title="最近设备版本分布" subtitle="帮助判断是否适合继续放量。">
        <SimpleTable
          rows={ota.deviceVersionDistribution}
          columns={[
            { key: "version", header: "版本", cell: (row) => row.versionName },
            { key: "android", header: "设备分组", cell: (row) => row.androidVersionGroup },
            { key: "count", header: "设备数", cell: (row) => row.deviceCount }
          ]}
          emptyLabel="暂无设备版本分布。"
        />
      </Panel>

      <Panel title="发布守则" subtitle="封闭测试阶段建议保持保守策略。">
        <ul className="list-plain">
          {ota.rolloutNotes.map((item) => (
            <li key={item}>{item}</li>
          ))}
        </ul>
      </Panel>

      <Panel title="快速调整状态" subtitle="暂停、恢复、完成和回滚都从这里走。">
        <div className="grid-2">
          {ota.releases.map((release) => (
            <form action={updateOtaReleaseStatusAction} className="stack-form" key={release.id}>
              <input name="releaseId" type="hidden" value={release.id} />
              <div className="toolbar-form__field"><label className="toolbar-form__label">版本</label><input className="toolbar-form__input" readOnly value={release.versionName} /></div>
              <div className="toolbar-form__field"><label className="toolbar-form__label">状态</label><select className="toolbar-form__select" defaultValue={release.rolloutStatus} name="rolloutStatus"><option value="draft">draft</option><option value="rolling">rolling</option><option value="paused">paused</option><option value="completed">completed</option><option value="rolled_back">rolled_back</option></select></div>
              <div className="toolbar-form__field"><label className="toolbar-form__label">灰度比例</label><input className="toolbar-form__input" defaultValue={release.rolloutPercent} name="rolloutPercent" /></div>
              <div className="toolbar-form__field"><label className="toolbar-form__label">目标设备数</label><input className="toolbar-form__input" defaultValue={release.deviceCount} name="deviceCount" /></div>
              <div className="toolbar-form__field"><label className="toolbar-form__label">安装成功率</label><input className="toolbar-form__input" defaultValue={release.installSuccessRate} name="installSuccessRate" /></div>
              <div className="toolbar-form__actions"><button className="toolbar-form__button" type="submit">保存状态</button></div>
            </form>
          ))}
        </div>
      </Panel>
    </Shell>
  );
}
