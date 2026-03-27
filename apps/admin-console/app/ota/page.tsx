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
  broadcast: "Regional broadcast",
  idle_background: "Idle fetch",
  next_boot: "Install on next boot",
  idle_apply: "Apply while idle",
} as const;

export default async function OtaPage({ searchParams }: OtaPageProps) {
  const ota = await fetchAdminOtaSnapshot();
  const params = (await searchParams) ?? {};
  const selectedTemplate =
    ota.rolloutTemplates.find((item) => item.id === params.template) ??
    ota.rolloutTemplates[1] ??
    ota.rolloutTemplates[0];

  return (
    <Shell eyebrow="Update" title="Client Updates">
      <StatsGrid
        items={[
          {
            label: "Rolling OTA",
            value: ota.metrics.activeRollouts,
            hint: "Package releases currently moving through rollout waves",
            tone: "accent",
          },
          {
            label: "Paused OTA",
            value: ota.metrics.pausedRollouts,
            hint: "Package releases temporarily held back",
            tone: "warn",
          },
          {
            label: "Version Groups",
            value: ota.metrics.versionGroups,
            hint: "Current client version distribution groups",
          },
          {
            label: "Upgradeable Devices",
            value: ota.metrics.otaReadyDevices,
            hint: "Estimated devices inside the active rollout scope",
          },
        ]}
      />

      <div className="grid-2">
        <Panel title="Publish OTA" subtitle="Package-level update. Notify by broadcast first, then let clients queue it while idle.">
          <form action={createOtaReleaseAction} className="stack-form">
            <div className="toolbar-form__field">
              <label className="toolbar-form__label" htmlFor="ota-version-name">Version Name</label>
              <input className="toolbar-form__input" id="ota-version-name" name="versionName" placeholder="0.1.10-beta" />
            </div>
            <div className="toolbar-form__field">
              <label className="toolbar-form__label" htmlFor="ota-version-code">Version Code</label>
              <input className="toolbar-form__input" id="ota-version-code" name="versionCode" placeholder="20" />
            </div>
            <div className="toolbar-form__field">
              <label className="toolbar-form__label" htmlFor="ota-release-channel">Channel</label>
              <select className="toolbar-form__select" defaultValue={selectedTemplate?.releaseChannel ?? "beta"} id="ota-release-channel" name="releaseChannel">
                <option value="stable">stable</option>
                <option value="beta">beta</option>
                <option value="internal">internal</option>
              </select>
            </div>
            <div className="toolbar-form__field">
              <label className="toolbar-form__label" htmlFor="ota-rollout-status">Status</label>
              <select className="toolbar-form__select" defaultValue={selectedTemplate?.rolloutStatus ?? "draft"} id="ota-rollout-status" name="rolloutStatus">
                <option value="draft">draft</option>
                <option value="rolling">rolling</option>
                <option value="paused">paused</option>
                <option value="completed">completed</option>
              </select>
            </div>
            <div className="toolbar-form__field">
              <label className="toolbar-form__label" htmlFor="ota-target-scope">Target Scope</label>
              <input className="toolbar-form__input" defaultValue={selectedTemplate?.targetScope ?? ""} id="ota-target-scope" name="targetScope" placeholder="radio-app / CN / Guangdong / android" />
            </div>
            <div className="toolbar-form__field">
              <label className="toolbar-form__label" htmlFor="ota-rollout-percent">Rollout Percent</label>
              <input className="toolbar-form__input" defaultValue={selectedTemplate?.rolloutPercent ?? 10} id="ota-rollout-percent" name="rolloutPercent" placeholder="10" />
            </div>
            <div className="toolbar-form__field">
              <label className="toolbar-form__label" htmlFor="ota-device-count">Target Devices</label>
              <input className="toolbar-form__input" defaultValue={selectedTemplate?.deviceCount ?? 12} id="ota-device-count" name="deviceCount" placeholder="12" />
            </div>
            <div className="toolbar-form__field">
              <label className="toolbar-form__label" htmlFor="ota-success-rate">Expected Success Rate</label>
              <input className="toolbar-form__input" defaultValue={selectedTemplate?.installSuccessRate ?? 100} id="ota-success-rate" name="installSuccessRate" placeholder="100" />
            </div>
            <div className="toolbar-form__field">
              <label className="toolbar-form__label" htmlFor="ota-artifact-url">Artifact URL</label>
              <input className="toolbar-form__input" id="ota-artifact-url" name="artifactUrl" placeholder="https://updates.sonance.app/radio-app/0.1.10/package.zip" />
            </div>
            <div className="toolbar-form__field">
              <label className="toolbar-form__label" htmlFor="ota-release-notes">Broadcast Copy</label>
              <textarea className="toolbar-form__textarea" id="ota-release-notes" name="releaseNotes" placeholder="Guangdong devices can upgrade to 0.1.10. Clients will fetch it quietly while idle and install on next boot." />
            </div>
            <div className="toolbar-form__actions">
              <button className="toolbar-form__button" type="submit">Publish OTA</button>
            </div>
          </form>
        </Panel>

        <Panel title="Publish Config Update" subtitle="Lightweight JSON config. Clients check in the background and apply while idle.">
          <form action={createClientConfigReleaseAction} className="stack-form">
            <div className="toolbar-form__field">
              <label className="toolbar-form__label" htmlFor="config-version-name">Config Version Name</label>
              <input className="toolbar-form__input" id="config-version-name" name="versionName" placeholder="cn-music-boost-v2" />
            </div>
            <div className="toolbar-form__field">
              <label className="toolbar-form__label" htmlFor="config-version-code">Config Version Code</label>
              <input className="toolbar-form__input" id="config-version-code" name="versionCode" placeholder="2" />
            </div>
            <div className="toolbar-form__field">
              <label className="toolbar-form__label" htmlFor="config-target-scope">Target Scope</label>
              <input className="toolbar-form__input" id="config-target-scope" name="targetScope" placeholder="radio-app / CN / Guangdong / music" />
            </div>
            <div className="toolbar-form__field">
              <label className="toolbar-form__label" htmlFor="config-key">Config Key</label>
              <input className="toolbar-form__input" defaultValue="radio-client-shell" id="config-key" name="configKey" />
            </div>
            <div className="toolbar-form__field">
              <label className="toolbar-form__label" htmlFor="config-rollout-status">Status</label>
              <select className="toolbar-form__select" defaultValue="draft" id="config-rollout-status" name="rolloutStatus">
                <option value="draft">draft</option>
                <option value="rolling">rolling</option>
                <option value="paused">paused</option>
                <option value="completed">completed</option>
              </select>
            </div>
            <div className="toolbar-form__field">
              <label className="toolbar-form__label" htmlFor="config-apply-policy">Apply Policy</label>
              <select className="toolbar-form__select" defaultValue="idle_apply" id="config-apply-policy" name="applyPolicy">
                <option value="idle_apply">idle_apply</option>
                <option value="next_boot">next_boot</option>
              </select>
            </div>
            <div className="toolbar-form__field">
              <label className="toolbar-form__label" htmlFor="config-payload-json">Config JSON</label>
              <textarea
                className="toolbar-form__textarea"
                id="config-payload-json"
                name="payloadJson"
                defaultValue={`{\n  "preferredMode": "music",\n  "backgroundImageUrl": "https://images.unsplash.com/photo-1493225457124-a3eb161ffa5f?auto=format&fit=crop&w=1200&q=80"\n}`}
              />
            </div>
            <div className="toolbar-form__field">
              <label className="toolbar-form__label" htmlFor="config-release-notes">Broadcast Copy</label>
              <textarea className="toolbar-form__textarea" id="config-release-notes" name="releaseNotes" placeholder="Music-first listeners in Guangdong can receive the new lightweight client config while idle." />
            </div>
            <div className="toolbar-form__actions">
              <button className="toolbar-form__button" type="submit">Publish Config Update</button>
            </div>
          </form>
        </Panel>
      </div>

      <Panel title="Execution Policy" subtitle="Clients stay local-first. Updates run only in the background and while idle.">
        <div className="grid-quick">
          <div className="quick-link">
            <div className="quick-link__title">{policyLabels.broadcast}</div>
            <div className="quick-link__caption">Publishing a release also creates a scoped system broadcast for that region.</div>
          </div>
          <div className="quick-link">
            <div className="quick-link__title">{policyLabels.idle_background}</div>
            <div className="quick-link__caption">Nothing blocks startup or interrupts voice and radio playback.</div>
          </div>
          <div className="quick-link">
            <div className="quick-link__title">{policyLabels.next_boot}</div>
            <div className="quick-link__caption">OTA packages stage first and install on the next boot window.</div>
          </div>
          <div className="quick-link">
            <div className="quick-link__title">{policyLabels.idle_apply}</div>
            <div className="quick-link__caption">Config updates can take effect without reinstalling the app.</div>
          </div>
        </div>
      </Panel>

      <div className="grid-2">
        <Panel title="OTA History" subtitle="Package releases, target scopes, and artifact URLs.">
          <SimpleTable
            rows={ota.releases}
            columns={[
              { key: "version", header: "Version", cell: (row) => <span className="mono">{row.versionName}</span> },
              {
                key: "status",
                header: "Status",
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
                ),
              },
              { key: "scope", header: "Scope", cell: (row) => row.targetScope },
              { key: "artifact", header: "Artifact", cell: (row) => row.artifactUrl ? <span className="mono">{row.artifactUrl}</span> : "-" },
              { key: "updated", header: "Updated", cell: (row) => formatDateTime(row.updatedAt) },
            ]}
            emptyLabel="No OTA releases"
          />
        </Panel>

        <Panel title="Config Update History" subtitle="Lightweight config releases with scoped idle apply rules.">
          <SimpleTable
            rows={ota.configReleases}
            columns={[
              { key: "version", header: "Version", cell: (row) => <span className="mono">{row.versionName}</span> },
              {
                key: "status",
                header: "Status",
                cell: (row) => (
                  <StatusChip
                    label={row.rolloutStatus}
                    tone={row.rolloutStatus === "rolling" ? "warn" : row.rolloutStatus === "completed" ? "ok" : "default"}
                  />
                ),
              },
              { key: "scope", header: "Scope", cell: (row) => row.targetScope },
              { key: "configKey", header: "Config Key", cell: (row) => row.configKey },
              { key: "policy", header: "Policy", cell: (row) => `${policyLabels[row.fetchPolicy]} / ${policyLabels[row.applyPolicy]}` },
            ]}
            emptyLabel="No config releases"
          />
        </Panel>
      </div>

      <Panel title="Quick Status Change" subtitle="Update OTA rollout state without re-entering the full release record.">
        <SimpleTable
          rows={ota.releases}
          columns={[
            { key: "version", header: "Version", cell: (row) => row.versionName },
            { key: "channel", header: "Channel", cell: (row) => <StatusChip label={row.releaseChannel} /> },
            { key: "scope", header: "Scope", cell: (row) => row.targetScope },
            {
              key: "action",
              header: "Set Status",
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
                  <button className="toolbar-form__button" type="submit">Update</button>
                </form>
              ),
            },
          ]}
          emptyLabel="No OTA releases to update"
        />
      </Panel>
    </Shell>
  );
}
