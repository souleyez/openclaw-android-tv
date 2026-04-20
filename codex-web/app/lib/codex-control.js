import { createHash, randomBytes, randomUUID } from 'node:crypto';
import fs from 'node:fs/promises';
import path from 'node:path';
import {
  archiveCodexThread as archiveLocalCodexThread,
  createCodexThread as createLocalCodexThread,
  getActiveCodexJob as getLocalActiveCodexJob,
  getCodexDefaults as getLocalCodexDefaults,
  getCodexModels as getLocalCodexModels,
  getCodexPageBootstrap as getLocalCodexPageBootstrap,
  listCodexThreads as listLocalCodexThreads,
  queueCodexResumeJob as queueLocalCodexResumeJob,
  runQueuedCodexJob as runLocalQueuedCodexJob,
  readCodexJob as readLocalCodexJob,
  readCodexThread as readLocalCodexThread,
} from '../../lib/codex-runtime-control.mjs';
import {
  archiveRelayThread,
  createRelayThread,
  getRelayActiveJob,
  getRelayBridgeStatus,
  getRelayDefaults,
  getRelayModels,
  getRelayPageBootstrap,
  isRelayModeEnabled,
  listRelayThreads,
  queueRelayResumeJob,
  readRelayJob,
  readRelayThread,
} from './codex-relay';
import {
  resolveCodexUploadSelections,
  resolveCodexUploadSelectionsForLocalExecution,
} from './codex-uploads';

const REPO_ROOT = process.cwd();
const CONTROL_STATE_DIR = String(process.env.CODEX_CONTROL_STATE_DIR || '').trim()
  || path.join(REPO_ROOT, '.storage', 'codex-mobile');
const CONTROL_BINDING_FILE = path.join(CONTROL_STATE_DIR, 'binding.json');

function hashToken(value) {
  return createHash('sha256').update(String(value || ''), 'utf8').digest('hex');
}

function describeDevice(userAgent) {
  const value = String(userAgent || '').trim();
  if (!value) {
    return 'Unknown browser';
  }

  if (/iPhone/i.test(value)) {
    return 'iPhone browser';
  }
  if (/iPad/i.test(value)) {
    return 'iPad browser';
  }
  if (/Android/i.test(value)) {
    return 'Android browser';
  }
  if (/Macintosh/i.test(value)) {
    return 'Mac browser';
  }
  if (/Windows/i.test(value)) {
    return 'Windows browser';
  }

  return 'Trusted browser';
}

function nowIso() {
  return new Date().toISOString();
}

function sanitizeBoundDevice(device) {
  if (!device) {
    return null;
  }

  return {
    id: device.id,
    label: device.label,
    userAgent: device.userAgent,
    boundAt: device.boundAt,
    lastSeenAt: device.lastSeenAt,
  };
}

async function ensureControlStateDirs() {
  await fs.mkdir(CONTROL_STATE_DIR, { recursive: true });
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

async function readBindingState() {
  await ensureControlStateDirs();
  return readJsonFile(CONTROL_BINDING_FILE, {
    trustedDevice: null,
    activeJobId: '',
  });
}

async function writeBindingState(nextState) {
  await ensureControlStateDirs();
  await writeJsonFileAtomic(CONTROL_BINDING_FILE, {
    trustedDevice: nextState?.trustedDevice || null,
    activeJobId: String(nextState?.activeJobId || '').trim(),
  });
}

function getBridgeBaseUrl() {
  return String(process.env.CODEX_BRIDGE_BASE_URL || '').trim().replace(/\/+$/, '');
}

function isRelayMode() {
  return isRelayModeEnabled();
}

function isBridgeMode() {
  return Boolean(getBridgeBaseUrl());
}

function buildBridgeHeaders(extraHeaders = {}) {
  const token = String(process.env.CODEX_BRIDGE_TOKEN || '').trim();
  return {
    ...(token ? { 'x-codex-bridge-token': token } : {}),
    ...extraHeaders,
  };
}

async function bridgeFetchJson(resourcePath, init = {}) {
  const baseUrl = getBridgeBaseUrl();
  if (!baseUrl) {
    const error = new Error('CODEX_BRIDGE_DISABLED');
    error.code = 'CODEX_BRIDGE_DISABLED';
    throw error;
  }

  const timeoutMs = Number(process.env.CODEX_BRIDGE_TIMEOUT_MS || 4000);
  const response = await fetch(`${baseUrl}${resourcePath}`, {
    cache: 'no-store',
    ...init,
    headers: buildBridgeHeaders(init.headers || {}),
    signal: AbortSignal.timeout(timeoutMs),
  }).catch((nextError) => {
    const message = nextError instanceof Error ? nextError.message : 'CODEX_BRIDGE_UNREACHABLE';
    const error = new Error(message);
    error.code = /aborted|timeout/i.test(message)
      ? 'CODEX_BRIDGE_TIMEOUT'
      : 'CODEX_BRIDGE_UNREACHABLE';
    throw error;
  });

  const payload = await response.json().catch(() => ({}));
  if (!response.ok) {
    const error = new Error(payload.error || payload.code || response.statusText || 'CODEX_BRIDGE_REQUEST_FAILED');
    error.code = payload.error || payload.code || 'CODEX_BRIDGE_REQUEST_FAILED';
    error.status = response.status;
    error.activeJob = payload.activeJob || null;
    throw error;
  }

  return payload;
}

function bridgeFailureBootstrap(error) {
  return {
    threads: [],
    defaults: {
      model: 'gpt-5.4',
      reasoningEffort: 'medium',
    },
    models: [],
    activeJob: null,
    selectedThread: null,
    selectedThreadId: '',
    bridgeStatus: {
      mode: 'remote',
      reachable: false,
      error: error?.code || (error instanceof Error ? error.message : 'CODEX_BRIDGE_UNREACHABLE'),
    },
  };
}

export async function getCodexBridgeStatus() {
  if (isRelayMode()) {
    return getRelayBridgeStatus();
  }

  if (!isBridgeMode()) {
    return {
      mode: 'local',
      reachable: true,
      error: '',
      lastSeenAt: nowIso(),
    };
  }

  try {
    await bridgeFetchJson('/bridge/jobs/active');
    return {
      mode: 'remote',
      reachable: true,
      error: '',
      lastSeenAt: '',
    };
  } catch (error) {
    return {
      mode: 'remote',
      reachable: false,
      error: error?.code || (error instanceof Error ? error.message : 'CODEX_BRIDGE_UNREACHABLE'),
      lastSeenAt: '',
    };
  }
}

export async function getCodexDefaults() {
  if (isRelayMode()) {
    return getRelayDefaults();
  }

  if (!isBridgeMode()) {
    return getLocalCodexDefaults();
  }

  const payload = await bridgeFetchJson('/bridge/defaults');
  return payload.item;
}

export async function getCodexModels() {
  if (isRelayMode()) {
    return getRelayModels();
  }

  if (!isBridgeMode()) {
    return getLocalCodexModels();
  }

  const payload = await bridgeFetchJson('/bridge/models');
  return payload.items || [];
}

export async function listCodexThreads(limit) {
  if (isRelayMode()) {
    return listRelayThreads(limit);
  }

  if (!isBridgeMode()) {
    return listLocalCodexThreads(limit);
  }

  const query = new URLSearchParams();
  if (typeof limit === 'number') {
    query.set('limit', String(limit));
  } else if (limit && typeof limit === 'object') {
    if (Number.isFinite(limit.limit)) {
      query.set('limit', String(Math.trunc(limit.limit)));
    }
    if (limit.archived) {
      query.set('archived', '1');
    }
  }

  const queryString = query.size ? `?${query.toString()}` : '';
  const payload = await bridgeFetchJson(`/bridge/threads${queryString}`);
  return payload.items || [];
}

export async function createCodexThread({
  model,
  reasoningEffort,
  cwd,
  threadName,
} = {}) {
  if (isRelayMode()) {
    return createRelayThread({
      model,
      reasoningEffort,
      cwd,
      threadName,
    });
  }

  if (!isBridgeMode()) {
    return createLocalCodexThread({
      model,
      reasoningEffort,
      cwd,
      threadName,
    });
  }

  const payload = await bridgeFetchJson('/bridge/threads', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({
      model,
      reasoningEffort,
      cwd,
      threadName,
    }),
  });
  return payload.item || null;
}

export async function archiveCodexThread(threadId) {
  if (isRelayMode()) {
    return archiveRelayThread(threadId);
  }

  if (!isBridgeMode()) {
    return archiveLocalCodexThread(threadId);
  }

  const payload = await bridgeFetchJson(`/bridge/threads/${encodeURIComponent(threadId)}`, {
    method: 'DELETE',
  });
  return payload.item || null;
}

export async function readCodexThread(sessionId, threadName = '') {
  if (isRelayMode()) {
    return readRelayThread(sessionId, threadName);
  }

  if (!isBridgeMode()) {
    return readLocalCodexThread(sessionId, threadName);
  }

  const payload = await bridgeFetchJson(
    `/bridge/threads/${encodeURIComponent(sessionId)}?threadName=${encodeURIComponent(threadName)}`,
  );
  return payload.item || null;
}

export async function bindTrustedDevice(userAgent) {
  const token = randomBytes(24).toString('hex');
  const state = await readBindingState();
  const timestamp = nowIso();
  const trustedDevice = {
    id: randomUUID(),
    tokenHash: hashToken(token),
    label: describeDevice(userAgent),
    userAgent: String(userAgent || '').trim(),
    boundAt: timestamp,
    lastSeenAt: timestamp,
  };

  await writeBindingState({
    ...state,
    trustedDevice,
  });

  return {
    token,
    device: sanitizeBoundDevice(trustedDevice),
  };
}

export async function getTrustedDeviceSummary() {
  const state = await readBindingState();
  return sanitizeBoundDevice(state.trustedDevice);
}

export async function clearTrustedDevice() {
  const state = await readBindingState();
  if (!state.trustedDevice) {
    return null;
  }

  const device = sanitizeBoundDevice(state.trustedDevice);
  await writeBindingState({
    ...state,
    trustedDevice: null,
  });
  return device;
}

export async function getTrustedDeviceForToken(token) {
  const state = await readBindingState();
  if (!token || !state.trustedDevice) {
    return null;
  }

  if (hashToken(token) !== state.trustedDevice.tokenHash) {
    return null;
  }

  return sanitizeBoundDevice(state.trustedDevice);
}

export async function touchTrustedDevice(token) {
  const state = await readBindingState();
  if (!token || !state.trustedDevice) {
    return null;
  }

  if (hashToken(token) !== state.trustedDevice.tokenHash) {
    return null;
  }

  state.trustedDevice.lastSeenAt = nowIso();
  await writeBindingState(state);
  return sanitizeBoundDevice(state.trustedDevice);
}

export async function getActiveCodexJob() {
  if (isRelayMode()) {
    return getRelayActiveJob();
  }

  if (!isBridgeMode()) {
    return getLocalActiveCodexJob();
  }

  const payload = await bridgeFetchJson('/bridge/jobs/active');
  return payload.item || null;
}

export async function readCodexJob(jobId) {
  if (isRelayMode()) {
    return readRelayJob(jobId);
  }

  if (!isBridgeMode()) {
    return readLocalCodexJob(jobId);
  }

  const payload = await bridgeFetchJson(`/bridge/jobs/${encodeURIComponent(jobId)}`);
  return payload.item || null;
}

export async function queueCodexResumeJob({
  sessionId,
  prompt,
  model,
  reasoningEffort,
  threadName,
  attachments,
}) {
  if (isRelayMode()) {
    const resolvedAttachments = await resolveCodexUploadSelections(attachments);
    return queueRelayResumeJob({
      sessionId,
      prompt,
      model,
      reasoningEffort,
      threadName,
      attachments: resolvedAttachments,
    });
  }

  if (!isBridgeMode()) {
    const resolvedAttachments = await resolveCodexUploadSelectionsForLocalExecution(attachments);
    return queueLocalCodexResumeJob({
      sessionId,
      prompt,
      model,
      reasoningEffort,
      threadName,
      attachments: resolvedAttachments,
    });
  }

  const resolvedAttachments = await resolveCodexUploadSelectionsForLocalExecution(attachments);

  const payload = await bridgeFetchJson(`/bridge/threads/${encodeURIComponent(sessionId)}/jobs`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({
      prompt,
      model,
      reasoningEffort,
      threadName,
      attachments: resolvedAttachments,
    }),
  });
  return payload.item || null;
}

export async function runQueuedCodexJob(jobId) {
  return runLocalQueuedCodexJob(jobId);
}

export async function getCodexPageBootstrap(limit) {
  if (isRelayMode()) {
    return getRelayPageBootstrap(limit);
  }

  if (!isBridgeMode()) {
    return getLocalCodexPageBootstrap(limit);
  }

  try {
  const query = typeof limit === 'number' ? `?limit=${limit}` : '';
  const payload = await bridgeFetchJson(`/bridge/bootstrap${query}`);
  return {
    ...payload.item,
    bridgeStatus: {
      mode: 'remote',
        reachable: true,
        error: '',
      },
    };
  } catch (error) {
    return bridgeFailureBootstrap(error);
  }
}
