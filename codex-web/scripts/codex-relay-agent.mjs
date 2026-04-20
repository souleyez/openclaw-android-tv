import { randomUUID } from 'node:crypto';
import fs from 'node:fs/promises';
import path from 'node:path';
import {
  archiveCodexThread,
  createCodexThread,
  getActiveCodexJob,
  getCodexDefaults,
  getCodexModels,
  listCodexThreads,
  queueCodexResumeJob,
  readCodexJob,
  readCodexThread,
  sendCodexDesktopAppMessage,
} from '../lib/codex-runtime-control.mjs';

const REPO_ROOT = process.cwd();
const RELAY_BASE_URL = String(process.env.CODEX_RELAY_BASE_URL || '').trim().replace(/\/+$/, '');
const RELAY_TOKEN = String(process.env.CODEX_RELAY_TOKEN || '').trim();
const POLL_INTERVAL_MS = Math.max(Number(process.env.CODEX_RELAY_POLL_INTERVAL_MS || 5000), 2000);
const THREAD_LIMIT = Math.min(Math.max(Number(process.env.CODEX_RELAY_THREADS_LIMIT || 24), 1), 24);
const AGENT_STATE_DIR = process.env.CODEX_RELAY_AGENT_STATE_DIR?.trim()
  || path.join(REPO_ROOT, '.storage', 'codex-relay-agent');
const AGENT_STATE_FILE = path.join(AGENT_STATE_DIR, 'state.json');
const AGENT_ID_FILE = path.join(AGENT_STATE_DIR, 'agent-id.txt');
const AGENT_UPLOADS_DIR = path.join(AGENT_STATE_DIR, 'uploads');
const AGENT_VERSION = '1';

function nowIso() {
  return new Date().toISOString();
}

function sleep(durationMs) {
  return new Promise((resolve) => {
    setTimeout(resolve, durationMs);
  });
}

function log(message, extra = '') {
  const suffix = extra ? ` ${extra}` : '';
  process.stdout.write(`[codex-relay-agent] ${message}${suffix}\n`);
}

function logError(error) {
  const code = error?.code || (error instanceof Error ? error.message : String(error));
  process.stderr.write(`[codex-relay-agent] error ${code}\n`);
}

async function ensureAgentStateDir() {
  await fs.mkdir(AGENT_STATE_DIR, { recursive: true });
}

async function ensureAgentUploadsDir() {
  await fs.mkdir(AGENT_UPLOADS_DIR, { recursive: true });
}

async function readJsonFile(filePath, fallback) {
  try {
    const raw = await fs.readFile(filePath, 'utf8');
    return JSON.parse(raw);
  } catch {
    return fallback;
  }
}

async function writeJsonFileAtomic(filePath, payload) {
  const tempPath = `${filePath}.${randomUUID()}.tmp`;
  await fs.writeFile(tempPath, JSON.stringify(payload, null, 2), 'utf8');
  await fs.rename(tempPath, filePath);
}

async function getAgentId() {
  await ensureAgentStateDir();

  try {
    const existing = (await fs.readFile(AGENT_ID_FILE, 'utf8')).trim();
    if (existing) {
      return existing;
    }
  } catch {
    // ignore
  }

  const agentId = randomUUID().replace(/-/g, '');
  await fs.writeFile(AGENT_ID_FILE, `${agentId}\n`, 'utf8');
  return agentId;
}

async function readAgentState() {
  await ensureAgentStateDir();
  const state = await readJsonFile(AGENT_STATE_FILE, {
    currentJob: null,
    currentControlId: '',
    pendingJobUpdate: null,
    pendingControlUpdate: null,
    threadUpdates: {},
    threadDetails: {},
  });

  return {
    currentJob: state?.currentJob && typeof state.currentJob === 'object'
      ? {
        serverJobId: String(state.currentJob.serverJobId || '').trim(),
        localJobId: String(state.currentJob.localJobId || '').trim(),
      }
      : null,
    pendingJobUpdate: state?.pendingJobUpdate && typeof state.pendingJobUpdate === 'object'
      ? {
        id: String(state.pendingJobUpdate.id || '').trim(),
        localJobId: String(state.pendingJobUpdate.localJobId || '').trim(),
        status: String(state.pendingJobUpdate.status || '').trim(),
        startedAt: String(state.pendingJobUpdate.startedAt || '').trim(),
        finishedAt: String(state.pendingJobUpdate.finishedAt || '').trim(),
        finalMessage: String(state.pendingJobUpdate.finalMessage || '').trim(),
        error: String(state.pendingJobUpdate.error || '').trim(),
        exitCode: typeof state.pendingJobUpdate.exitCode === 'number' ? state.pendingJobUpdate.exitCode : null,
      }
      : null,
    currentControlId: String(state.currentControlId || '').trim(),
    pendingControlUpdate: state?.pendingControlUpdate && typeof state.pendingControlUpdate === 'object'
      ? {
        id: String(state.pendingControlUpdate.id || '').trim(),
        status: String(state.pendingControlUpdate.status || '').trim(),
        updatedAt: String(state.pendingControlUpdate.updatedAt || '').trim(),
        resultThread: state.pendingControlUpdate.resultThread && typeof state.pendingControlUpdate.resultThread === 'object'
          ? {
            id: String(state.pendingControlUpdate.resultThread.id || '').trim(),
            threadName: String(state.pendingControlUpdate.resultThread.threadName || '').trim(),
            updatedAt: String(state.pendingControlUpdate.resultThread.updatedAt || '').trim(),
          }
          : null,
        error: String(state.pendingControlUpdate.error || '').trim(),
      }
      : null,
    threadUpdates: state?.threadUpdates && typeof state.threadUpdates === 'object'
      ? state.threadUpdates
      : {},
    threadDetails: state?.threadDetails && typeof state.threadDetails === 'object'
      ? state.threadDetails
      : {},
  };
}

async function writeAgentState(state) {
  await ensureAgentStateDir();
  await writeJsonFileAtomic(AGENT_STATE_FILE, {
    currentJob: state?.currentJob || null,
    currentControlId: String(state?.currentControlId || '').trim(),
    pendingJobUpdate: state?.pendingJobUpdate || null,
    pendingControlUpdate: state?.pendingControlUpdate || null,
    threadUpdates: state?.threadUpdates || {},
    threadDetails: state?.threadDetails || {},
  });
}

function sanitizeUploadFilename(value) {
  const filename = path.basename(String(value || '').trim()) || 'upload';
  return filename.replace(/[<>:"/\\|?*\u0000-\u001f]+/g, '-').trim() || 'upload';
}

async function downloadRelayAttachments(serverJob) {
  const attachments = Array.isArray(serverJob?.attachments) ? serverJob.attachments : [];
  if (!attachments.length) {
    return [];
  }

  await ensureAgentUploadsDir();
  const jobDir = path.join(AGENT_UPLOADS_DIR, String(serverJob.id || 'job'));
  await fs.mkdir(jobDir, { recursive: true });

  const resolved = [];
  for (let index = 0; index < attachments.length; index += 1) {
    const attachment = attachments[index];
    const attachmentId = String(attachment?.id || '').trim();
    if (!attachmentId) {
      throw new Error('CODEX_UPLOAD_NOT_FOUND');
    }

    const response = await fetch(`${RELAY_BASE_URL}/api/codex/uploads/${encodeURIComponent(attachmentId)}/content`, {
      headers: {
        'x-codex-relay-token': RELAY_TOKEN,
      },
      cache: 'no-store',
    });
    if (!response.ok) {
      const payload = await response.json().catch(() => ({}));
      const error = new Error(payload.error || payload.code || 'CODEX_UPLOAD_FETCH_FAILED');
      error.code = payload.error || payload.code || 'CODEX_UPLOAD_FETCH_FAILED';
      throw error;
    }

    const localFilename = `${String(index + 1).padStart(2, '0')}-${sanitizeUploadFilename(attachment.filename)}`;
    const localPath = path.join(jobDir, localFilename);
    const buffer = Buffer.from(await response.arrayBuffer());
    await fs.writeFile(localPath, buffer);

    resolved.push({
      id: attachmentId,
      filename: sanitizeUploadFilename(attachment.filename),
      contentType: String(attachment?.contentType || '').trim() || 'application/octet-stream',
      mediaType: String(attachment?.mediaType || '').trim() || 'file',
      size: typeof attachment?.size === 'number' ? attachment.size : buffer.length,
      localPath,
    });
  }

  return resolved;
}

function sanitizeLocalJobUpdate(serverJobId, localJob) {
  if (!serverJobId || !localJob) {
    return null;
  }

  const localStatus = String(localJob.status || '').trim();
  const status = localStatus === 'queued'
    ? 'dispatched'
    : localStatus === 'running'
      ? 'running'
      : localStatus === 'succeeded'
        ? 'succeeded'
        : 'failed';

  return {
    id: serverJobId,
    localJobId: String(localJob.id || '').trim(),
    status,
    startedAt: String(localJob.startedAt || '').trim(),
    finishedAt: String(localJob.finishedAt || '').trim(),
    finalMessage: String(localJob.finalMessage || '').trim(),
    error: String(localJob.error || '').trim(),
    exitCode: typeof localJob.exitCode === 'number' ? localJob.exitCode : null,
  };
}

async function buildJobUpdate(agentState) {
  if (agentState.pendingJobUpdate?.id) {
    return agentState.pendingJobUpdate;
  }

  if (!agentState.currentJob?.serverJobId || !agentState.currentJob?.localJobId) {
    return null;
  }

  const localJob = await readCodexJob(agentState.currentJob.localJobId);
  if (!localJob) {
    return {
      id: agentState.currentJob.serverJobId,
      localJobId: agentState.currentJob.localJobId,
      status: 'failed',
      startedAt: '',
      finishedAt: nowIso(),
      finalMessage: '',
      error: 'CODEX_LOCAL_JOB_NOT_FOUND',
      exitCode: null,
    };
  }

  return sanitizeLocalJobUpdate(agentState.currentJob.serverJobId, localJob);
}

async function buildControlUpdate(agentState) {
  if (agentState.pendingControlUpdate?.id) {
    return agentState.pendingControlUpdate;
  }

  return null;
}

async function buildSnapshot(agentState) {
  const [defaults, models, threads, archivedThreads, activeJob] = await Promise.all([
    getCodexDefaults(),
    getCodexModels(),
    listCodexThreads(THREAD_LIMIT),
    listCodexThreads({
      limit: THREAD_LIMIT,
      archived: true,
    }),
    getActiveCodexJob(),
  ]);

  const threadDetails = {};
  const threadUpdates = {};
  const pendingLoads = [];

  for (const thread of threads) {
    const threadId = String(thread.id || '').trim();
    if (!threadId) {
      continue;
    }

    const updatedAt = String(thread.updatedAt || '').trim();
    const cachedDetail = agentState.threadDetails?.[threadId] || null;
    const cachedUpdatedAt = String(agentState.threadUpdates?.[threadId] || '').trim();
    const hasCachedMessages = Array.isArray(cachedDetail?.messages);
    if (cachedDetail && hasCachedMessages && cachedUpdatedAt && cachedUpdatedAt === updatedAt) {
      threadDetails[threadId] = cachedDetail;
      threadUpdates[threadId] = updatedAt;
      continue;
    }

    pendingLoads.push(
      readCodexThread(threadId, thread.threadName).then((detail) => ({
        threadId,
        updatedAt,
        detail,
      })),
    );
  }

  const loadedDetails = await Promise.all(pendingLoads);
  for (const item of loadedDetails) {
    if (!item.detail) {
      continue;
    }

    threadDetails[item.threadId] = item.detail;
    threadUpdates[item.threadId] = item.updatedAt || String(item.detail.updatedAt || '').trim();
  }

  for (const [threadId, detail] of Object.entries(agentState.threadDetails || {})) {
    if (threadDetails[threadId]) {
      continue;
    }

    const summary = threads.find((item) => item.id === threadId);
    if (!summary) {
      continue;
    }

    threadDetails[threadId] = detail;
    threadUpdates[threadId] = String(agentState.threadUpdates?.[threadId] || summary.updatedAt || '').trim();
  }

  const selectedThreadId = String(activeJob?.sessionId || threads[0]?.id || '').trim();
  let selectedThread = selectedThreadId ? threadDetails[selectedThreadId] || null : null;
  if (!selectedThread && selectedThreadId) {
    const selectedSummary = threads.find((item) => item.id === selectedThreadId);
    selectedThread = await readCodexThread(selectedThreadId, selectedSummary?.threadName || '');
    if (selectedThread) {
      threadDetails[selectedThreadId] = selectedThread;
      threadUpdates[selectedThreadId] = String(selectedThread.updatedAt || '').trim();
    }
  }

  return {
    snapshot: {
      updatedAt: nowIso(),
      defaults,
      models,
      threads,
      archivedThreads,
      threadDetails,
      activeJob,
      selectedThreadId,
      selectedThread,
    },
    nextThreadDetails: threadDetails,
    nextThreadUpdates: threadUpdates,
  };
}

async function syncWithRelay(agentId, payload) {
  const response = await fetch(`${RELAY_BASE_URL}/api/codex/relay/agent/sync`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'x-codex-relay-token': RELAY_TOKEN,
      'x-codex-relay-agent-id': agentId,
      'x-codex-relay-agent-version': AGENT_VERSION,
    },
    body: JSON.stringify(payload),
    cache: 'no-store',
  });

  const body = await response.json().catch(() => ({}));
  if (!response.ok) {
    const error = new Error(body.error || body.code || response.statusText || 'CODEX_RELAY_SYNC_FAILED');
    error.code = body.error || body.code || 'CODEX_RELAY_SYNC_FAILED';
    throw error;
  }

  return body.item || {};
}

async function ensureServerJobStarted(serverJob, agentState, snapshot) {
  if (!serverJob?.id) {
    return agentState;
  }

  if (agentState.currentJob?.serverJobId === serverJob.id) {
    return agentState;
  }

  try {
    const selectedThreadId = String(snapshot?.selectedThreadId || '').trim();
    const selectedThread = selectedThreadId
      ? snapshot?.threadDetails?.[selectedThreadId] || snapshot?.selectedThread || null
      : null;
    const hasAttachments = Array.isArray(serverJob?.attachments) && serverJob.attachments.length > 0;
    const shouldSendViaDesktopApp = Boolean(
      selectedThreadId
      && selectedThreadId === String(serverJob.sessionId || '').trim()
      && selectedThread?.running
      && !hasAttachments,
    );

    if (shouldSendViaDesktopApp) {
      try {
        const dispatched = await sendCodexDesktopAppMessage(serverJob.prompt);
        log('sent message to active desktop thread', serverJob.id);
        return {
          ...agentState,
          currentJob: null,
          pendingJobUpdate: {
            id: serverJob.id,
            localJobId: 'desktop-app',
            status: 'succeeded',
            startedAt: dispatched.acceptedAt,
            finishedAt: dispatched.acceptedAt,
            finalMessage: '',
            error: '',
            exitCode: 0,
          },
        };
      } catch (error) {
        const code = error?.code || (error instanceof Error ? error.message : 'CODEX_APP_SERVER_SEND_FAILED');
        log('desktop app direct send failed, falling back to local job', `${serverJob.id} ${code}`);
      }
    }

    const localJob = await queueCodexResumeJob({
      sessionId: serverJob.sessionId,
      prompt: serverJob.prompt,
      model: serverJob.model,
      reasoningEffort: serverJob.reasoningEffort,
      threadName: serverJob.threadName,
      attachments: await downloadRelayAttachments(serverJob),
    });

    log('started local job', `${serverJob.id} -> ${localJob.id}`);
    return {
      ...agentState,
      currentJob: {
        serverJobId: serverJob.id,
        localJobId: localJob.id,
      },
      pendingJobUpdate: null,
    };
  } catch (error) {
    const code = error?.code || (error instanceof Error ? error.message : 'CODEX_LOCAL_JOB_START_FAILED');
    if (code === 'CODEX_JOB_ALREADY_RUNNING') {
      return agentState;
    }

    return {
      ...agentState,
      pendingJobUpdate: {
        id: serverJob.id,
        localJobId: '',
        status: 'failed',
        startedAt: '',
        finishedAt: nowIso(),
        finalMessage: '',
        error: code,
        exitCode: null,
      },
    };
  }
}

async function ensureServerControlHandled(serverControl, agentState) {
  if (!serverControl?.id) {
    return {
      nextState: agentState,
      handled: false,
    };
  }

  if (
    agentState.currentControlId === serverControl.id
    || agentState.pendingControlUpdate?.id === serverControl.id
  ) {
    return {
      nextState: agentState,
      handled: false,
    };
  }

  try {
    if (serverControl.type === 'thread.create') {
      const thread = await createCodexThread({
        model: serverControl.model,
        reasoningEffort: serverControl.reasoningEffort,
        cwd: serverControl.cwd,
        threadName: serverControl.threadName,
      });

      return {
        nextState: {
          ...agentState,
          currentControlId: serverControl.id,
          pendingControlUpdate: {
            id: serverControl.id,
            status: 'succeeded',
            updatedAt: nowIso(),
            resultThread: {
              id: String(thread.id || '').trim(),
              threadName: String(thread.threadName || thread.id || '').trim(),
              updatedAt: String(thread.updatedAt || '').trim(),
            },
            error: '',
          },
        },
        handled: true,
      };
    }

    if (serverControl.type === 'thread.archive') {
      const archived = await archiveCodexThread(serverControl.threadId);
      return {
        nextState: {
          ...agentState,
          currentControlId: serverControl.id,
          pendingControlUpdate: {
            id: serverControl.id,
            status: 'succeeded',
            updatedAt: String(archived.archivedAt || nowIso()).trim(),
            resultThread: null,
            error: '',
          },
        },
        handled: true,
      };
    }

    return {
      nextState: {
        ...agentState,
        currentControlId: serverControl.id,
        pendingControlUpdate: {
          id: serverControl.id,
          status: 'failed',
          updatedAt: nowIso(),
          resultThread: null,
          error: 'CODEX_RELAY_CONTROL_UNSUPPORTED',
        },
      },
      handled: true,
    };
  } catch (error) {
    const code = error?.code || (error instanceof Error ? error.message : 'CODEX_RELAY_CONTROL_FAILED');
    return {
      nextState: {
        ...agentState,
        currentControlId: serverControl.id,
        pendingControlUpdate: {
          id: serverControl.id,
          status: 'failed',
          updatedAt: nowIso(),
          resultThread: null,
          error: code,
        },
      },
      handled: true,
    };
  }
}

async function runLoop() {
  if (!RELAY_BASE_URL) {
    throw new Error('CODEX_RELAY_BASE_URL_REQUIRED');
  }
  if (!RELAY_TOKEN) {
    throw new Error('CODEX_RELAY_TOKEN_REQUIRED');
  }

  const agentId = await getAgentId();
  log('agent online', `${RELAY_BASE_URL} as ${agentId}`);

  while (true) {
    try {
      const agentState = await readAgentState();
      const [snapshotPayload, jobUpdate, controlUpdate] = await Promise.all([
        buildSnapshot(agentState),
        buildJobUpdate(agentState),
        buildControlUpdate(agentState),
      ]);

      const syncPayload = {
        snapshot: snapshotPayload.snapshot,
        jobUpdate,
        controlUpdate,
      };
      const response = await syncWithRelay(agentId, syncPayload);

      let nextState = {
        ...agentState,
        threadDetails: snapshotPayload.nextThreadDetails,
        threadUpdates: snapshotPayload.nextThreadUpdates,
      };

      const currentJobTerminal = Boolean(jobUpdate?.id && ['succeeded', 'failed'].includes(jobUpdate.status));
      if (agentState.pendingJobUpdate?.id && agentState.pendingJobUpdate.id === jobUpdate?.id) {
        nextState.pendingJobUpdate = null;
      }

      if (currentJobTerminal && agentState.currentJob?.serverJobId === jobUpdate.id) {
        nextState.currentJob = null;
      }

      if (agentState.pendingControlUpdate?.id && agentState.pendingControlUpdate.id === controlUpdate?.id) {
        nextState.pendingControlUpdate = null;
        if (nextState.currentControlId === controlUpdate.id) {
          nextState.currentControlId = '';
        }
      }

      nextState = await ensureServerJobStarted(response.job, nextState, snapshotPayload.snapshot);
      const controlResult = await ensureServerControlHandled(response.control, nextState);
      nextState = controlResult.nextState;
      await writeAgentState(nextState);

      if (controlResult.handled) {
        continue;
      }
    } catch (error) {
      logError(error);
    }

    await sleep(POLL_INTERVAL_MS);
  }
}

runLoop().catch((error) => {
  logError(error);
  process.exitCode = 1;
});
