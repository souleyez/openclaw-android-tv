import { createHash, randomUUID, timingSafeEqual } from 'node:crypto';
import fs from 'node:fs/promises';
import path from 'node:path';

const REPO_ROOT = process.cwd();
const RELAY_STATE_DIR = String(process.env.CODEX_RELAY_STATE_DIR || '').trim()
  || path.join(REPO_ROOT, '.storage', 'codex-relay');
const RELAY_STATE_FILE = path.join(RELAY_STATE_DIR, 'state.json');
const RELAY_JOBS_DIR = path.join(RELAY_STATE_DIR, 'jobs');
const RELAY_CONTROLS_DIR = path.join(RELAY_STATE_DIR, 'controls');
const DEFAULT_THREADS_LIMIT = 24;
const MAX_THREADS_LIMIT = 40;
const RELAY_OFFLINE_THRESHOLD_MS = Math.max(
  Number(process.env.CODEX_RELAY_OFFLINE_THRESHOLD_MS || 45_000),
  10_000,
);
const RELAY_CONTROL_WAIT_TIMEOUT_MS = Math.max(
  Number(process.env.CODEX_RELAY_CONTROL_WAIT_TIMEOUT_MS || 20_000),
  5_000,
);
const RELAY_CONTROL_WAIT_POLL_MS = Math.max(
  Number(process.env.CODEX_RELAY_CONTROL_WAIT_POLL_MS || 250),
  100,
);

function nowIso() {
  return new Date().toISOString();
}

function hashValue(value) {
  return createHash('sha256').update(String(value || ''), 'utf8').digest('hex');
}

function secureStringEqual(left, right) {
  const leftBuffer = Buffer.from(String(left || ''), 'utf8');
  const rightBuffer = Buffer.from(String(right || ''), 'utf8');
  if (leftBuffer.length !== rightBuffer.length) {
    return false;
  }

  return timingSafeEqual(leftBuffer, rightBuffer);
}

function relayError(code, extra = {}) {
  const error = new Error(code);
  error.code = code;
  Object.assign(error, extra);
  return error;
}

function clampThreadsLimit(value) {
  if (!Number.isFinite(value) || value <= 0) {
    return DEFAULT_THREADS_LIMIT;
  }

  return Math.min(Math.max(Math.trunc(value), 1), MAX_THREADS_LIMIT);
}

function trimString(value) {
  return String(value || '').trim();
}

function trimLongText(value, limit = 12_000) {
  const text = trimString(value);
  return text.length > limit ? text.slice(0, limit) : text;
}

function sleep(durationMs) {
  return new Promise((resolve) => {
    setTimeout(resolve, durationMs);
  });
}

function parseThreadListOptions(limitOrOptions = DEFAULT_THREADS_LIMIT) {
  if (typeof limitOrOptions === 'number') {
    return {
      limit: clampThreadsLimit(limitOrOptions),
      archived: false,
    };
  }

  if (!limitOrOptions || typeof limitOrOptions !== 'object') {
    return {
      limit: DEFAULT_THREADS_LIMIT,
      archived: false,
    };
  }

  return {
    limit: clampThreadsLimit(Number(limitOrOptions.limit)),
    archived: Boolean(limitOrOptions.archived),
  };
}

function sanitizeDefaults(input) {
  return {
    model: trimString(input?.model) || 'gpt-5.4',
    reasoningEffort: trimString(input?.reasoningEffort) || 'medium',
  };
}

function sanitizeReasoningLevel(item) {
  const effort = trimString(item?.effort);
  if (!effort) {
    return null;
  }

  return {
    effort,
    description: trimString(item?.description),
  };
}

function sanitizeModel(item) {
  const slug = trimString(item?.slug);
  if (!slug) {
    return null;
  }

  const supportedReasoningLevels = Array.isArray(item?.supportedReasoningLevels)
    ? item.supportedReasoningLevels
      .map(sanitizeReasoningLevel)
      .filter(Boolean)
    : [];

  return {
    slug,
    displayName: trimString(item?.displayName) || slug,
    description: trimString(item?.description),
    defaultReasoningLevel: trimString(item?.defaultReasoningLevel)
      || supportedReasoningLevels[0]?.effort
      || 'medium',
    supportedReasoningLevels,
  };
}

function sanitizeThreadSummary(item) {
  const id = trimString(item?.id);
  if (!id) {
    return null;
  }

  return {
    id,
    threadName: trimString(item?.threadName) || id,
    updatedAt: trimString(item?.updatedAt),
  };
}

function sanitizeThreadMessage(item) {
  const id = trimString(item?.id);
  const role = trimString(item?.role);
  const text = trimLongText(item?.text);
  if (!id || !role || !text) {
    return null;
  }

  return {
    id,
    role,
    text,
    truncated: Boolean(item?.truncated),
    timestamp: trimString(item?.timestamp),
    phase: trimString(item?.phase),
  };
}

function sanitizeThreadDetail(item) {
  const id = trimString(item?.id);
  if (!id) {
    return null;
  }

  return {
    id,
    threadName: trimString(item?.threadName) || id,
    sessionFile: trimString(item?.sessionFile),
    cwd: trimString(item?.cwd),
    model: trimString(item?.model) || 'gpt-5.4',
    reasoningEffort: trimString(item?.reasoningEffort) || 'medium',
    updatedAt: trimString(item?.updatedAt),
    originator: trimString(item?.originator),
    cliVersion: trimString(item?.cliVersion),
    running: Boolean(item?.running),
    latestReply: trimLongText(item?.latestReply),
    latestReplyTruncated: Boolean(item?.latestReplyTruncated),
    latestUserMessage: trimLongText(item?.latestUserMessage, 4_000),
    latestUserMessageTruncated: Boolean(item?.latestUserMessageTruncated),
    messages: Array.isArray(item?.messages)
      ? item.messages.map(sanitizeThreadMessage).filter(Boolean)
      : [],
  };
}

function sanitizePublicJob(job) {
  if (!job) {
    return null;
  }

  const id = trimString(job.id);
  if (!id) {
    return null;
  }

  return {
    id,
    sessionId: trimString(job.sessionId),
    threadName: trimString(job.threadName),
    prompt: trimLongText(job.prompt, 4_000),
    model: trimString(job.model),
    reasoningEffort: trimString(job.reasoningEffort) || 'medium',
    status: trimString(job.status) || 'queued',
    createdAt: trimString(job.createdAt),
    dispatchedAt: trimString(job.dispatchedAt),
    startedAt: trimString(job.startedAt),
    finishedAt: trimString(job.finishedAt),
    exitCode: typeof job.exitCode === 'number' ? job.exitCode : null,
    finalMessage: trimLongText(job.finalMessage),
    error: trimLongText(job.error, 2_000),
    localJobId: trimString(job.localJobId),
    attachments: Array.isArray(job.attachments)
      ? job.attachments
        .map((item) => {
          const attachmentId = trimString(item?.id);
          if (!attachmentId) {
            return null;
          }

          const mediaType = trimString(item?.mediaType);
          if (!['image', 'file'].includes(mediaType)) {
            return null;
          }

          return {
            id: attachmentId,
            filename: trimString(item?.filename) || attachmentId,
            contentType: trimString(item?.contentType) || 'application/octet-stream',
            mediaType,
            size: typeof item?.size === 'number' ? item.size : 0,
          };
        })
        .filter(Boolean)
      : [],
  };
}

function sanitizeControlThreadSummary(item) {
  const summary = sanitizeThreadSummary(item);
  return summary || null;
}

function sanitizePublicControl(control) {
  if (!control) {
    return null;
  }

  const id = trimString(control.id);
  const type = trimString(control.type);
  if (!id || !type) {
    return null;
  }

  const status = trimString(control.status) || 'queued';
  if (!['queued', 'dispatched', 'succeeded', 'failed'].includes(status)) {
    return null;
  }

  return {
    id,
    type,
    status,
    createdAt: trimString(control.createdAt),
    updatedAt: trimString(control.updatedAt),
    threadId: trimString(control.threadId),
    threadName: trimString(control.threadName),
    model: trimString(control.model),
    reasoningEffort: trimString(control.reasoningEffort),
    cwd: trimString(control.cwd),
    resultThread: sanitizeControlThreadSummary(control.resultThread),
    error: trimLongText(control.error, 2_000),
  };
}

function sanitizeSnapshot(input, threadLimit = DEFAULT_THREADS_LIMIT) {
  const threads = Array.isArray(input?.threads)
    ? input.threads
      .map(sanitizeThreadSummary)
      .filter(Boolean)
      .slice(0, clampThreadsLimit(threadLimit))
    : [];
  const archivedThreads = Array.isArray(input?.archivedThreads)
    ? input.archivedThreads
      .map(sanitizeThreadSummary)
      .filter(Boolean)
      .slice(0, clampThreadsLimit(threadLimit))
    : [];

  const allowedThreadIds = new Set(threads.map((item) => item.id));
  const rawThreadDetails = input?.threadDetails && typeof input.threadDetails === 'object'
    ? input.threadDetails
    : {};
  const threadDetails = {};

  for (const [threadId, detail] of Object.entries(rawThreadDetails)) {
    if (!allowedThreadIds.has(threadId)) {
      continue;
    }

    const sanitized = sanitizeThreadDetail(detail);
    if (sanitized) {
      threadDetails[sanitized.id] = sanitized;
    }
  }

  const selectedThreadId = trimString(input?.selectedThreadId);
  const selectedThread = sanitizeThreadDetail(input?.selectedThread);
  if (selectedThread && allowedThreadIds.has(selectedThread.id)) {
    threadDetails[selectedThread.id] = selectedThread;
  }

  return {
    updatedAt: trimString(input?.updatedAt) || nowIso(),
    defaults: sanitizeDefaults(input?.defaults),
    models: Array.isArray(input?.models)
      ? input.models.map(sanitizeModel).filter(Boolean)
      : [],
    threads,
    archivedThreads,
    threadDetails,
    activeJob: sanitizePublicJob(input?.activeJob),
    selectedThreadId: allowedThreadIds.has(selectedThreadId) ? selectedThreadId : '',
    selectedThread: selectedThread && allowedThreadIds.has(selectedThread.id)
      ? selectedThread
      : null,
  };
}

function defaultRelayState() {
  return {
    version: 2,
    activeJobId: '',
    activeControlId: '',
    agent: {
      boundAgentIdHash: '',
      firstSeenAt: '',
      lastSeenAt: '',
      lastSnapshotAt: '',
      version: '',
    },
    snapshot: sanitizeSnapshot({}, DEFAULT_THREADS_LIMIT),
  };
}

async function ensureRelayStateDirs() {
  await fs.mkdir(RELAY_STATE_DIR, { recursive: true });
  await fs.mkdir(RELAY_JOBS_DIR, { recursive: true });
  await fs.mkdir(RELAY_CONTROLS_DIR, { recursive: true });
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

async function readRelayState() {
  await ensureRelayStateDirs();
  const state = await readJsonFile(RELAY_STATE_FILE, defaultRelayState());
  return {
    version: 2,
    activeJobId: trimString(state?.activeJobId),
    activeControlId: trimString(state?.activeControlId),
    agent: {
      boundAgentIdHash: trimString(state?.agent?.boundAgentIdHash),
      firstSeenAt: trimString(state?.agent?.firstSeenAt),
      lastSeenAt: trimString(state?.agent?.lastSeenAt),
      lastSnapshotAt: trimString(state?.agent?.lastSnapshotAt),
      version: trimString(state?.agent?.version),
    },
    snapshot: sanitizeSnapshot(state?.snapshot, DEFAULT_THREADS_LIMIT),
  };
}

async function writeRelayState(state) {
  await ensureRelayStateDirs();
  await writeJsonFileAtomic(RELAY_STATE_FILE, {
    version: 2,
    activeJobId: trimString(state?.activeJobId),
    activeControlId: trimString(state?.activeControlId),
    agent: {
      boundAgentIdHash: trimString(state?.agent?.boundAgentIdHash),
      firstSeenAt: trimString(state?.agent?.firstSeenAt),
      lastSeenAt: trimString(state?.agent?.lastSeenAt),
      lastSnapshotAt: trimString(state?.agent?.lastSnapshotAt),
      version: trimString(state?.agent?.version),
    },
    snapshot: sanitizeSnapshot(state?.snapshot, DEFAULT_THREADS_LIMIT),
  });
}

function getJobFilePath(jobId) {
  return path.join(RELAY_JOBS_DIR, `${jobId}.json`);
}

function getControlFilePath(controlId) {
  return path.join(RELAY_CONTROLS_DIR, `${controlId}.json`);
}

async function readRelayJobRecord(jobId) {
  const id = trimString(jobId);
  if (!id) {
    return null;
  }

  const record = await readJsonFile(getJobFilePath(id), null);
  return sanitizePublicJob(record);
}

async function writeRelayJobRecord(job) {
  const record = sanitizePublicJob(job);
  if (!record) {
    throw relayError('CODEX_RELAY_JOB_INVALID');
  }

  await ensureRelayStateDirs();
  await writeJsonFileAtomic(getJobFilePath(record.id), record);
  return record;
}

async function readRelayControlRecord(controlId) {
  const id = trimString(controlId);
  if (!id) {
    return null;
  }

  const record = await readJsonFile(getControlFilePath(id), null);
  return sanitizePublicControl(record);
}

async function writeRelayControlRecord(control) {
  const record = sanitizePublicControl(control);
  if (!record) {
    throw relayError('CODEX_RELAY_CONTROL_INVALID');
  }

  await ensureRelayStateDirs();
  await writeJsonFileAtomic(getControlFilePath(record.id), record);
  return record;
}

function isTerminalJob(job) {
  return ['succeeded', 'failed', 'cancelled'].includes(trimString(job?.status));
}

function isTerminalControl(control) {
  return ['succeeded', 'failed'].includes(trimString(control?.status));
}

function relayReachable(state) {
  const lastSeenAt = Date.parse(trimString(state?.agent?.lastSeenAt));
  if (!Number.isFinite(lastSeenAt)) {
    return false;
  }

  return (Date.now() - lastSeenAt) <= RELAY_OFFLINE_THRESHOLD_MS;
}

function buildRelayBridgeStatus(state) {
  const reachable = relayReachable(state);
  return {
    mode: 'relay',
    reachable,
    error: reachable ? '' : 'CODEX_RELAY_OFFLINE',
    lastSeenAt: trimString(state?.agent?.lastSeenAt),
  };
}

function configuredRelayMode() {
  const value = trimString(process.env.CODEX_RELAY_MODE).toLowerCase();
  return value === '1' || value === 'true' || value === 'relay';
}

export function isRelayModeEnabled() {
  return configuredRelayMode();
}

function getConfiguredRelayToken() {
  return trimString(process.env.CODEX_RELAY_TOKEN);
}

function resolveReasoningEffort(models, model, requestedEffort, defaultEffort) {
  const modelEntry = models.find((item) => item.slug === model) || null;
  const supported = new Set((modelEntry?.supportedReasoningLevels || []).map((item) => item.effort));

  if (requestedEffort && supported.has(requestedEffort)) {
    return requestedEffort;
  }
  if (defaultEffort && supported.has(defaultEffort)) {
    return defaultEffort;
  }

  return modelEntry?.defaultReasoningLevel || defaultEffort || 'medium';
}

async function readActiveRelayJob(state) {
  const activeJobId = trimString(state?.activeJobId);
  if (!activeJobId) {
    return null;
  }

  const activeJob = await readRelayJobRecord(activeJobId);
  if (!activeJob || isTerminalJob(activeJob)) {
    if (activeJobId) {
      state.activeJobId = '';
      await writeRelayState(state);
    }
    return null;
  }

  return activeJob;
}

async function readActiveRelayControl(state) {
  const activeControlId = trimString(state?.activeControlId);
  if (!activeControlId) {
    return null;
  }

  const activeControl = await readRelayControlRecord(activeControlId);
  if (!activeControl || isTerminalControl(activeControl)) {
    if (activeControlId) {
      state.activeControlId = '';
      await writeRelayState(state);
    }
    return null;
  }

  return activeControl;
}

export async function getRelayDefaults() {
  const state = await readRelayState();
  return state.snapshot.defaults;
}

export async function getRelayBridgeStatus() {
  const state = await readRelayState();
  return buildRelayBridgeStatus(state);
}

export async function getRelayModels() {
  const state = await readRelayState();
  return state.snapshot.models;
}

export async function listRelayThreads(limitOrOptions = DEFAULT_THREADS_LIMIT) {
  const state = await readRelayState();
  const { limit, archived } = parseThreadListOptions(limitOrOptions);
  const source = archived ? state.snapshot.archivedThreads : state.snapshot.threads;
  return source.slice(0, limit);
}

export async function readRelayThread(sessionId) {
  const state = await readRelayState();
  return state.snapshot.threadDetails[trimString(sessionId)] || null;
}

export async function getRelayActiveJob() {
  const state = await readRelayState();
  return readActiveRelayJob(state);
}

export async function readRelayJob(jobId) {
  return readRelayJobRecord(jobId);
}

export async function getRelayPageBootstrap(limit = DEFAULT_THREADS_LIMIT) {
  const state = await readRelayState();
  const threads = state.snapshot.threads.slice(0, clampThreadsLimit(limit));
  const activeJob = await readActiveRelayJob(state);
  const selectedThreadId = trimString(activeJob?.sessionId)
    || trimString(state.snapshot.selectedThreadId)
    || trimString(threads[0]?.id);
  const selectedThread = state.snapshot.threadDetails[selectedThreadId]
    || (state.snapshot.selectedThread?.id === selectedThreadId ? state.snapshot.selectedThread : null)
    || null;

  return {
    threads,
    defaults: state.snapshot.defaults,
    models: state.snapshot.models,
    activeJob,
    selectedThread,
    selectedThreadId,
    bridgeStatus: buildRelayBridgeStatus(state),
  };
}

async function waitForRelayControl(controlId, timeoutMs = RELAY_CONTROL_WAIT_TIMEOUT_MS) {
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    const control = await readRelayControlRecord(controlId);
    if (!control) {
      break;
    }

    if (isTerminalControl(control)) {
      return control;
    }

    await sleep(RELAY_CONTROL_WAIT_POLL_MS);
  }

  throw relayError('CODEX_RELAY_CONTROL_TIMEOUT');
}

function buildControlError(control, fallbackCode) {
  const code = trimString(control?.error) || fallbackCode;
  const error = relayError(code);
  error.control = control || null;
  return error;
}

export async function createRelayThread({
  model,
  reasoningEffort,
  cwd,
  threadName,
} = {}) {
  const state = await readRelayState();
  if (!relayReachable(state)) {
    throw relayError('CODEX_RELAY_OFFLINE');
  }

  const activeControl = await readActiveRelayControl(state);
  if (activeControl) {
    throw relayError('CODEX_RELAY_CONTROL_BUSY', {
      control: activeControl,
    });
  }

  const defaults = state.snapshot.defaults;
  const models = state.snapshot.models;
  const resolvedModel = trimString(model) || defaults.model;
  const modelEntry = models.find((item) => item.slug === resolvedModel) || null;
  if (models.length && !modelEntry) {
    throw relayError('CODEX_MODEL_INVALID');
  }

  const control = sanitizePublicControl({
    id: randomUUID(),
    type: 'thread.create',
    status: 'queued',
    createdAt: nowIso(),
    updatedAt: nowIso(),
    threadId: '',
    threadName: trimString(threadName),
    model: resolvedModel,
    reasoningEffort: resolveReasoningEffort(
      models,
      resolvedModel,
      trimString(reasoningEffort),
      defaults.reasoningEffort,
    ),
    cwd: trimString(cwd) || '',
    resultThread: null,
    error: '',
  });

  await writeRelayControlRecord(control);
  state.activeControlId = control.id;
  await writeRelayState(state);

  const settled = await waitForRelayControl(control.id);
  if (settled.status !== 'succeeded' || !settled.resultThread) {
    throw buildControlError(settled, 'CODEX_THREAD_CREATE_FAILED');
  }

  return {
    ...settled.resultThread,
    model: control.model,
    reasoningEffort: control.reasoningEffort,
    cwd: control.cwd,
  };
}

export async function archiveRelayThread(threadId) {
  const nextThreadId = trimString(threadId);
  if (!nextThreadId) {
    throw relayError('CODEX_THREAD_NOT_FOUND');
  }

  const state = await readRelayState();
  if (!relayReachable(state)) {
    throw relayError('CODEX_RELAY_OFFLINE');
  }

  const activeControl = await readActiveRelayControl(state);
  if (activeControl) {
    throw relayError('CODEX_RELAY_CONTROL_BUSY', {
      control: activeControl,
    });
  }

  if (!state.snapshot.threads.some((item) => item.id === nextThreadId)) {
    throw relayError('CODEX_THREAD_NOT_FOUND');
  }

  const control = sanitizePublicControl({
    id: randomUUID(),
    type: 'thread.archive',
    status: 'queued',
    createdAt: nowIso(),
    updatedAt: nowIso(),
    threadId: nextThreadId,
    threadName: '',
    model: '',
    reasoningEffort: '',
    cwd: '',
    resultThread: null,
    error: '',
  });

  await writeRelayControlRecord(control);
  state.activeControlId = control.id;
  await writeRelayState(state);

  const settled = await waitForRelayControl(control.id);
  if (settled.status !== 'succeeded') {
    throw buildControlError(settled, 'CODEX_THREAD_ARCHIVE_FAILED');
  }

  return {
    id: nextThreadId,
    archivedAt: settled.updatedAt || nowIso(),
  };
}

export async function queueRelayResumeJob({
  sessionId,
  prompt,
  model,
  reasoningEffort,
  threadName,
  attachments,
}) {
  const state = await readRelayState();
  if (!relayReachable(state)) {
    throw relayError('CODEX_RELAY_OFFLINE');
  }

  const activeJob = await readActiveRelayJob(state);
  if (activeJob) {
    throw relayError('CODEX_JOB_ALREADY_RUNNING', {
      activeJob,
    });
  }

  if (state.snapshot.activeJob && ['queued', 'running'].includes(state.snapshot.activeJob.status)) {
    throw relayError('CODEX_DESKTOP_BUSY', {
      activeJob: state.snapshot.activeJob,
    });
  }

  const nextSessionId = trimString(sessionId);
  if (!nextSessionId) {
    throw relayError('CODEX_THREAD_NOT_FOUND');
  }

  const nextPrompt = trimString(prompt);
  if (!nextPrompt) {
    throw relayError('CODEX_PROMPT_REQUIRED');
  }

  const threadSummary = state.snapshot.threads.find((item) => item.id === nextSessionId);
  if (!threadSummary) {
    throw relayError('CODEX_THREAD_NOT_FOUND');
  }

  const defaults = state.snapshot.defaults;
  const models = state.snapshot.models;
  const resolvedModel = trimString(model) || defaults.model;
  const modelEntry = models.find((item) => item.slug === resolvedModel) || null;
  if (models.length && !modelEntry) {
    throw relayError('CODEX_MODEL_INVALID');
  }

  const resolvedReasoningEffort = resolveReasoningEffort(
    models,
    resolvedModel,
    trimString(reasoningEffort),
    defaults.reasoningEffort,
  );

  const job = sanitizePublicJob({
    id: randomUUID(),
    sessionId: nextSessionId,
    threadName: trimString(threadName) || threadSummary.threadName || nextSessionId,
    prompt: nextPrompt,
    model: resolvedModel,
    reasoningEffort: resolvedReasoningEffort,
    status: 'queued',
    createdAt: nowIso(),
    dispatchedAt: '',
    startedAt: '',
    finishedAt: '',
    exitCode: null,
    finalMessage: '',
    error: '',
    localJobId: '',
    attachments: Array.isArray(attachments) ? attachments : [],
  });

  await writeRelayJobRecord(job);
  state.activeJobId = job.id;
  await writeRelayState(state);
  return job;
}

function parseAgentId(request) {
  const agentId = trimString(request.headers.get('x-codex-relay-agent-id'));
  if (!/^[A-Za-z0-9._:-]{16,128}$/.test(agentId)) {
    throw relayError('CODEX_RELAY_AGENT_ID_INVALID');
  }
  return agentId;
}

async function authorizeRelayAgent(request) {
  if (!configuredRelayMode()) {
    throw relayError('CODEX_RELAY_DISABLED');
  }

  const configuredToken = getConfiguredRelayToken();
  if (!configuredToken) {
    throw relayError('CODEX_RELAY_TOKEN_REQUIRED');
  }

  const requestToken = trimString(request.headers.get('x-codex-relay-token'));
  if (!requestToken || !secureStringEqual(requestToken, configuredToken)) {
    throw relayError('CODEX_RELAY_UNAUTHORIZED');
  }

  return {
    agentId: parseAgentId(request),
    agentVersion: trimString(request.headers.get('x-codex-relay-agent-version')),
  };
}

function sanitizeJobUpdate(input) {
  if (!input || typeof input !== 'object') {
    return null;
  }

  const id = trimString(input.id);
  if (!id) {
    return null;
  }

  const status = trimString(input.status) || 'running';
  if (!['queued', 'dispatched', 'running', 'succeeded', 'failed'].includes(status)) {
    return null;
  }

  return {
    id,
    localJobId: trimString(input.localJobId),
    status,
    startedAt: trimString(input.startedAt),
    finishedAt: trimString(input.finishedAt),
    finalMessage: trimLongText(input.finalMessage),
    error: trimLongText(input.error, 2_000),
    exitCode: typeof input.exitCode === 'number' ? input.exitCode : null,
  };
}

function sanitizeControlUpdate(input) {
  if (!input || typeof input !== 'object') {
    return null;
  }

  const id = trimString(input.id);
  if (!id) {
    return null;
  }

  const status = trimString(input.status) || 'failed';
  if (!['succeeded', 'failed'].includes(status)) {
    return null;
  }

  return {
    id,
    status,
    updatedAt: trimString(input.updatedAt) || nowIso(),
    resultThread: sanitizeControlThreadSummary(input.resultThread),
    error: trimLongText(input.error, 2_000),
  };
}

async function applyJobUpdate(state, jobUpdate) {
  const update = sanitizeJobUpdate(jobUpdate);
  if (!update) {
    return null;
  }

  const existing = await readRelayJobRecord(update.id);
  if (!existing) {
    return null;
  }

  const nextJob = sanitizePublicJob({
    ...existing,
    status: update.status,
    localJobId: update.localJobId || existing.localJobId,
    startedAt: update.startedAt || existing.startedAt || (update.status === 'running' ? nowIso() : ''),
    finishedAt: update.finishedAt || existing.finishedAt || (isTerminalJob(update) ? nowIso() : ''),
    exitCode: update.exitCode ?? existing.exitCode,
    finalMessage: update.finalMessage || existing.finalMessage,
    error: update.error || (update.status === 'failed' ? existing.error : ''),
  });

  await writeRelayJobRecord(nextJob);

  if (state.activeJobId === nextJob.id && isTerminalJob(nextJob)) {
    state.activeJobId = '';
  }

  return nextJob;
}

async function applyControlUpdate(state, controlUpdate) {
  const update = sanitizeControlUpdate(controlUpdate);
  if (!update) {
    return null;
  }

  const existing = await readRelayControlRecord(update.id);
  if (!existing) {
    return null;
  }

  const nextControl = sanitizePublicControl({
    ...existing,
    status: update.status,
    updatedAt: update.updatedAt,
    resultThread: update.resultThread || existing.resultThread,
    error: update.error || '',
  });

  await writeRelayControlRecord(nextControl);
  if (state.activeControlId === nextControl.id && isTerminalControl(nextControl)) {
    state.activeControlId = '';
  }

  return nextControl;
}

async function ensureDispatchableActiveJob(state) {
  const activeJob = await readActiveRelayJob(state);
  if (!activeJob) {
    return null;
  }

  if (activeJob.status === 'queued') {
    const nextJob = sanitizePublicJob({
      ...activeJob,
      status: 'dispatched',
      dispatchedAt: activeJob.dispatchedAt || nowIso(),
    });
    await writeRelayJobRecord(nextJob);
    return nextJob;
  }

  return activeJob;
}

async function ensureDispatchableActiveControl(state) {
  const activeControl = await readActiveRelayControl(state);
  if (!activeControl) {
    return null;
  }

  if (activeControl.status === 'queued') {
    const nextControl = sanitizePublicControl({
      ...activeControl,
      status: 'dispatched',
      updatedAt: nowIso(),
    });
    await writeRelayControlRecord(nextControl);
    return nextControl;
  }

  return activeControl;
}

export async function syncRelayAgent(request, payload) {
  const { agentId, agentVersion } = await authorizeRelayAgent(request);
  const state = await readRelayState();
  const agentHash = hashValue(agentId);
  if (state.agent.boundAgentIdHash && state.agent.boundAgentIdHash !== agentHash) {
    throw relayError('CODEX_RELAY_AGENT_MISMATCH');
  }

  const timestamp = nowIso();
  if (!state.agent.boundAgentIdHash) {
    state.agent.boundAgentIdHash = agentHash;
    state.agent.firstSeenAt = timestamp;
  }

  state.agent.lastSeenAt = timestamp;
  state.agent.lastSnapshotAt = timestamp;
  state.agent.version = agentVersion || state.agent.version;

  const snapshot = sanitizeSnapshot(payload?.snapshot, DEFAULT_THREADS_LIMIT);
  state.snapshot = snapshot;

  await applyJobUpdate(state, payload?.jobUpdate);
  await applyControlUpdate(state, payload?.controlUpdate);
  const job = await ensureDispatchableActiveJob(state);
  const control = await ensureDispatchableActiveControl(state);
  await writeRelayState(state);

  return {
    job: job
      ? {
        id: job.id,
        sessionId: job.sessionId,
        threadName: job.threadName,
        prompt: job.prompt,
        model: job.model,
        reasoningEffort: job.reasoningEffort,
        status: job.status,
        attachments: Array.isArray(job.attachments) ? job.attachments : [],
      }
      : null,
    control: control
      ? {
        id: control.id,
        type: control.type,
        status: control.status,
        threadId: control.threadId,
        threadName: control.threadName,
        model: control.model,
        reasoningEffort: control.reasoningEffort,
        cwd: control.cwd,
      }
      : null,
    bridgeStatus: buildRelayBridgeStatus(state),
  };
}
