import { spawn } from 'node:child_process';
import { randomUUID } from 'node:crypto';
import fs from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';

const CODEX_HOME = process.env.CODEX_HOME?.trim() || path.join(os.homedir(), '.codex');
const REPO_ROOT = process.cwd();
const CONTROL_STATE_DIR = process.env.CODEX_BRIDGE_STATE_DIR?.trim()
  || path.join(REPO_ROOT, '.storage', 'codex-bridge');
const CONTROL_STATE_FILE = path.join(CONTROL_STATE_DIR, 'state.json');
const CONTROL_JOBS_DIR = path.join(CONTROL_STATE_DIR, 'jobs');
const RUNTIME_CACHE_DIR = path.join(CONTROL_STATE_DIR, 'runtime');
const CONTROL_THREADS_FILE = path.join(CONTROL_STATE_DIR, 'threads.json');
const SESSION_INDEX_FILE = path.join(CODEX_HOME, 'session_index.jsonl');
const SESSION_DIRS = [
  {
    directory: path.join(CODEX_HOME, 'sessions'),
    archived: false,
  },
  {
    directory: path.join(CODEX_HOME, 'archived_sessions'),
    archived: true,
  },
];
const MODELS_CACHE_FILE = path.join(CODEX_HOME, 'models_cache.json');
const CONFIG_FILE = path.join(CODEX_HOME, 'config.toml');
const RUNNER_SCRIPT = path.join(REPO_ROOT, 'scripts', 'codex-mobile-runner.mjs');
const DESKTOP_CODEX_COPY_PATH = path.join(
  RUNTIME_CACHE_DIR,
  process.platform === 'win32' ? 'codex-desktop.exe' : 'codex-desktop',
);
const CODEX_EXEC_TIMEOUT_MS = Math.max(Number(process.env.CODEX_EXEC_TIMEOUT_MS || 10 * 60 * 1000), 30_000);
const CODEX_APP_SERVER_SEND_TIMEOUT_MS = Math.max(Number(process.env.CODEX_APP_SERVER_SEND_TIMEOUT_MS || 20_000), 5_000);
const CODEX_APP_SERVER_CONTROL_TIMEOUT_MS = Math.max(
  Number(process.env.CODEX_APP_SERVER_CONTROL_TIMEOUT_MS || 20_000),
  5_000,
);
const THREAD_FILE_CACHE_TTL_MS = 2_000;
const MAX_THREADS = 40;
const MAX_REPLY_CHARS = 12_000;
const MAX_THREAD_MESSAGES = 60;
const MAX_JOB_ATTACHMENTS = Math.max(Number(process.env.CODEX_MAX_JOB_ATTACHMENTS || 8), 1);

const threadFileCache = {
  loadedAt: 0,
  map: new Map(),
};

function nowIso() {
  return new Date().toISOString();
}

function trimString(value) {
  return String(value || '').trim();
}

function clampThreadLimit(value, fallback = MAX_THREADS) {
  if (!Number.isFinite(value) || value <= 0) {
    return fallback;
  }

  return Math.min(Math.max(Math.trunc(value), 1), MAX_THREADS);
}

function parseThreadListOptions(limitOrOptions = MAX_THREADS) {
  if (typeof limitOrOptions === 'number') {
    return {
      limit: clampThreadLimit(limitOrOptions),
      archived: false,
    };
  }

  if (!limitOrOptions || typeof limitOrOptions !== 'object') {
    return {
      limit: MAX_THREADS,
      archived: false,
    };
  }

  return {
    limit: clampThreadLimit(Number(limitOrOptions.limit), MAX_THREADS),
    archived: Boolean(limitOrOptions.archived),
  };
}

function unixSecondsToIso(value) {
  const seconds = Number(value);
  if (!Number.isFinite(seconds) || seconds <= 0) {
    return '';
  }

  return new Date(seconds * 1000).toISOString();
}

function trimMessage(value, limit = MAX_REPLY_CHARS) {
  const text = String(value || '').trim();
  if (!text) {
    return {
      text: '',
      truncated: false,
    };
  }

  if (text.length <= limit) {
    return {
      text,
      truncated: false,
    };
  }

  return {
    text: `${text.slice(0, limit)}\n\n[truncated]`,
    truncated: true,
  };
}

function safeJsonParse(value) {
  try {
    return JSON.parse(value);
  } catch {
    return null;
  }
}

function extractContentText(content) {
  if (!Array.isArray(content)) {
    return '';
  }

  return content
    .map((item) => {
      if (!item || typeof item !== 'object') {
        return '';
      }

      if (typeof item.text === 'string') {
        return item.text;
      }

      return '';
    })
    .filter(Boolean)
    .join('\n\n')
    .trim();
}

function extractUserInputText(content) {
  if (!Array.isArray(content)) {
    return '';
  }

  return content
    .map((item) => {
      if (!item || typeof item !== 'object') {
        return '';
      }

      if (item.type === 'text' && typeof item.text === 'string') {
        return item.text;
      }

      if (item.type === 'image' && typeof item.url === 'string') {
        return `[image] ${item.url}`;
      }

      if (item.type === 'localImage' && typeof item.path === 'string') {
        return `[image] ${item.path}`;
      }

      if (item.type === 'skill' && typeof item.name === 'string') {
        return `[$${item.name}]`;
      }

      if (item.type === 'mention' && typeof item.name === 'string') {
        return `[@${item.name}]`;
      }

      return '';
    })
    .filter(Boolean)
    .join('\n\n')
    .trim();
}

function appendThreadMessage(messages, {
  role,
  text,
  timestamp,
  phase = '',
}) {
  const trimmed = trimMessage(text);
  if (!trimmed.text) {
    return;
  }

  const previous = messages[messages.length - 1];
  if (previous && previous.role === role && previous.text === trimmed.text) {
    if (!previous.timestamp && timestamp) {
      previous.timestamp = timestamp;
    }
    return;
  }

  messages.push({
    id: `${role}-${messages.length + 1}`,
    role,
    text: trimmed.text,
    truncated: trimmed.truncated,
    timestamp: timestamp || '',
    phase,
  });
}

function sanitizeJob(job) {
  if (!job) {
    return null;
  }

  return {
    id: job.id,
    sessionId: job.sessionId,
    prompt: job.prompt,
    model: job.model,
    reasoningEffort: job.reasoningEffort,
    status: job.status,
    createdAt: job.createdAt,
    startedAt: job.startedAt || '',
    finishedAt: job.finishedAt || '',
    exitCode: typeof job.exitCode === 'number' ? job.exitCode : null,
    finalMessage: job.finalMessage || '',
    error: job.error || '',
    threadName: job.threadName || '',
    attachments: Array.isArray(job.attachments)
      ? job.attachments
        .map((item) => {
          const mediaType = trimString(item?.mediaType);
          if (!trimString(item?.id) || !trimString(item?.filename) || !['image', 'file'].includes(mediaType)) {
            return null;
          }

          return {
            id: trimString(item.id),
            filename: trimString(item.filename),
            contentType: trimString(item.contentType) || 'application/octet-stream',
            mediaType,
            size: typeof item?.size === 'number' ? item.size : 0,
          };
        })
        .filter(Boolean)
      : [],
  };
}

function sanitizeJobAttachment(item, { includeLocalPath = false } = {}) {
  const id = trimString(item?.id);
  const filename = trimString(item?.filename);
  const mediaType = trimString(item?.mediaType);
  const localPath = trimString(item?.localPath);
  if (!id || !filename || !['image', 'file'].includes(mediaType)) {
    return null;
  }

  return {
    id,
    filename,
    contentType: trimString(item?.contentType) || 'application/octet-stream',
    mediaType,
    size: typeof item?.size === 'number' ? item.size : 0,
    ...(includeLocalPath && localPath ? { localPath } : {}),
  };
}

async function resolveQueuedAttachments(attachments) {
  const items = Array.isArray(attachments) ? attachments : [];
  if (items.length > MAX_JOB_ATTACHMENTS) {
    const error = new Error('CODEX_ATTACHMENTS_TOO_MANY');
    error.code = 'CODEX_ATTACHMENTS_TOO_MANY';
    throw error;
  }

  const resolved = [];
  for (const item of items) {
    const attachment = sanitizeJobAttachment(item, { includeLocalPath: true });
    if (!attachment?.localPath) {
      const error = new Error('CODEX_UPLOAD_NOT_FOUND');
      error.code = 'CODEX_UPLOAD_NOT_FOUND';
      throw error;
    }

    const stat = await fs.stat(attachment.localPath).catch(() => null);
    if (!stat?.isFile()) {
      const error = new Error('CODEX_UPLOAD_NOT_FOUND');
      error.code = 'CODEX_UPLOAD_NOT_FOUND';
      throw error;
    }

    resolved.push({
      ...attachment,
      size: attachment.size || stat.size,
    });
  }

  return resolved;
}

function buildPromptWithFileAttachments(prompt, attachments) {
  const fileAttachments = (attachments || []).filter((item) => item.mediaType === 'file' && item.localPath);
  if (!fileAttachments.length) {
    return prompt;
  }

  return [
    'Attached local files:',
    ...fileAttachments.map((item) => `- ${item.filename}: ${item.localPath}`),
    '',
    prompt,
  ].join('\n');
}

function buildTurnInputForJob(job) {
  const attachments = Array.isArray(job.attachments)
    ? job.attachments
      .map((item) => sanitizeJobAttachment(item, { includeLocalPath: true }))
      .filter(Boolean)
    : [];
  const input = [
    {
      type: 'text',
      text: buildPromptWithFileAttachments(job.prompt, attachments),
      text_elements: [],
    },
  ];

  for (const item of attachments) {
    if (item.mediaType !== 'image' || !item.localPath) {
      continue;
    }

    input.push({
      type: 'localImage',
      path: item.localPath,
    });
  }

  return input;
}

function normalizeJobError(result) {
  const stderr = String(result?.stderr || '').trim();
  if (!stderr) {
    return 'CODEX_TURN_FAILED';
  }

  if (/enoent|not recognized|cannot find|找不到/i.test(stderr)) {
    return `CODEX_PROCESS_LAUNCH_FAILED: ${stderr}`;
  }

  if (/timed out|timeout|aborted/i.test(stderr)) {
    return `CODEX_TURN_TIMEOUT: ${stderr}`;
  }

  if (/stream disconnected|responseStreamDisconnected|os error 10054/i.test(stderr)) {
    return `CODEX_TURN_STREAM_FAILED: ${stderr}`;
  }

  return stderr;
}

function normalizeAppServerError(error) {
  const code = String(error?.code || '').trim();
  if (code) {
    return code;
  }

  const stderr = String(error?.stderr || '').trim();
  if (/timed out|timeout|aborted/i.test(stderr)) {
    return 'CODEX_TURN_TIMEOUT';
  }

  return stderr || 'CODEX_TURN_FAILED';
}

function invalidateThreadFileCache() {
  threadFileCache.loadedAt = 0;
  threadFileCache.map = new Map();
}

async function pathExists(targetPath) {
  try {
    await fs.access(targetPath);
    return true;
  } catch {
    return false;
  }
}

async function ensureControlStateDirs() {
  await fs.mkdir(CONTROL_STATE_DIR, { recursive: true });
  await fs.mkdir(CONTROL_JOBS_DIR, { recursive: true });
  await fs.mkdir(RUNTIME_CACHE_DIR, { recursive: true });
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

async function readRuntimeState() {
  await ensureControlStateDirs();
  return readJsonFile(CONTROL_STATE_FILE, {
    activeJobId: '',
  });
}

async function writeRuntimeState(nextState) {
  await ensureControlStateDirs();
  await writeJsonFileAtomic(CONTROL_STATE_FILE, {
    activeJobId: String(nextState?.activeJobId || '').trim(),
  });
}

async function readPendingThreadRegistry() {
  await ensureControlStateDirs();
  const payload = await readJsonFile(CONTROL_THREADS_FILE, {
    threads: [],
  });

  return Array.isArray(payload?.threads)
    ? payload.threads
      .map((item) => {
        const id = trimString(item?.id);
        if (!id) {
          return null;
        }

        return {
          id,
          threadName: trimString(item?.threadName) || id,
          updatedAt: trimString(item?.updatedAt),
          cwd: trimString(item?.cwd),
          model: trimString(item?.model),
          reasoningEffort: trimString(item?.reasoningEffort),
        };
      })
      .filter(Boolean)
    : [];
}

async function writePendingThreadRegistry(threads) {
  await ensureControlStateDirs();
  await writeJsonFileAtomic(CONTROL_THREADS_FILE, {
    threads,
  });
}

async function upsertPendingThread(summary) {
  const nextSummary = {
    id: trimString(summary?.id),
    threadName: trimString(summary?.threadName),
    updatedAt: trimString(summary?.updatedAt) || nowIso(),
    cwd: trimString(summary?.cwd),
    model: trimString(summary?.model),
    reasoningEffort: trimString(summary?.reasoningEffort),
  };
  if (!nextSummary.id) {
    return;
  }

  const current = await readPendingThreadRegistry();
  const next = current.filter((item) => item.id !== nextSummary.id);
  next.push(nextSummary);
  await writePendingThreadRegistry(next);
}

async function removePendingThread(threadId) {
  const nextThreadId = trimString(threadId);
  if (!nextThreadId) {
    return;
  }

  const current = await readPendingThreadRegistry();
  const next = current.filter((item) => item.id !== nextThreadId);
  if (next.length === current.length) {
    return;
  }

  await writePendingThreadRegistry(next);
}

function getJobFilePath(jobId) {
  return path.join(CONTROL_JOBS_DIR, `${jobId}.json`);
}

function getJobReplyPath(jobId) {
  return path.join(CONTROL_JOBS_DIR, `${jobId}.reply.txt`);
}

function getJobEventsPath(jobId) {
  return path.join(CONTROL_JOBS_DIR, `${jobId}.events.jsonl`);
}

async function readJobRecord(jobId) {
  if (!jobId) {
    return null;
  }

  return readJsonFile(getJobFilePath(jobId), null);
}

async function writeJobRecord(jobId, payload) {
  await ensureControlStateDirs();
  await writeJsonFileAtomic(getJobFilePath(jobId), payload);
}

async function clearFinishedActiveJob() {
  const state = await readRuntimeState();
  if (!state.activeJobId) {
    return null;
  }

  const activeJob = await readJobRecord(state.activeJobId);
  if (!activeJob || !['queued', 'running'].includes(activeJob.status)) {
    await writeRuntimeState({
      ...state,
      activeJobId: '',
    });
    return sanitizeJob(activeJob);
  }

  return sanitizeJob(activeJob);
}

async function collectSessionFiles(directory, target, archived = false) {
  let entries = [];
  try {
    entries = await fs.readdir(directory, { withFileTypes: true });
  } catch {
    return;
  }

  await Promise.all(entries.map(async (entry) => {
    const nextPath = path.join(directory, entry.name);
    if (entry.isDirectory()) {
      await collectSessionFiles(nextPath, target, archived);
      return;
    }

    if (!entry.isFile() || !entry.name.endsWith('.jsonl')) {
      return;
    }

    const match = entry.name.match(/([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})\.jsonl$/i);
    if (!match) {
      return;
    }

    const sessionId = match[1];
    if (!target.has(sessionId)) {
      target.set(sessionId, {
        path: nextPath,
        archived,
      });
    }
  }));
}

async function getSessionFileMap() {
  const now = Date.now();
  if (threadFileCache.loadedAt && (now - threadFileCache.loadedAt) < THREAD_FILE_CACHE_TTL_MS) {
    return threadFileCache.map;
  }

  const nextMap = new Map();
  for (const sessionDir of SESSION_DIRS) {
    await collectSessionFiles(sessionDir.directory, nextMap, sessionDir.archived);
  }

  threadFileCache.loadedAt = now;
  threadFileCache.map = nextMap;
  return nextMap;
}

async function resolveSessionEntry(sessionId) {
  const fileMap = await getSessionFileMap();
  return fileMap.get(sessionId) || null;
}

async function resolveSessionFile(sessionId, { allowArchived = false } = {}) {
  const entry = await resolveSessionEntry(sessionId);
  if (!entry || (entry.archived && !allowArchived)) {
    return '';
  }

  return entry.path;
}

function buildAppServerInitMessage(id = 1) {
  return {
    method: 'initialize',
    id,
    params: {
      clientInfo: {
        name: 'home-mobile-bridge',
        title: 'home mobile bridge',
        version: '0.0.1',
      },
      capabilities: null,
    },
  };
}

function mapAppServerRequestError(responseError, fallbackCode) {
  const rawCode = trimString(
    responseError?.code
    || responseError?.type
    || responseError?.message,
  );
  const rawMessage = trimString(responseError?.message);
  const normalized = `${rawCode} ${rawMessage}`.toLowerCase();
  if (normalized.includes('thread') && normalized.includes('not') && normalized.includes('found')) {
    return 'CODEX_THREAD_NOT_FOUND';
  }
  if (normalized.includes('model') && (normalized.includes('invalid') || normalized.includes('not found'))) {
    return 'CODEX_MODEL_INVALID';
  }
  if (normalized.includes('failed to archive thread')) {
    return 'CODEX_THREAD_ARCHIVE_FAILED';
  }
  if (normalized.includes('timeout')) {
    return 'CODEX_APP_SERVER_TIMEOUT';
  }
  return rawCode || fallbackCode;
}

async function runCodexAppServerRequests(codexExecutable, requests, options = {}) {
  return new Promise((resolve, reject) => {
    const queue = [
      {
        ...buildAppServerInitMessage(1),
        errorCode: 'CODEX_APP_SERVER_INIT_FAILED',
      },
      ...requests.map((request, index) => ({
        method: request.method,
        id: index + 2,
        params: request.params,
        errorCode: request.errorCode || 'CODEX_APP_SERVER_REQUEST_FAILED',
      })),
    ];

    const child = spawn(codexExecutable, ['app-server'], {
      detached: false,
      stdio: ['pipe', 'pipe', 'pipe'],
      windowsHide: true,
    });

    const timeoutMs = Math.max(
      Number(options.timeoutMs || CODEX_APP_SERVER_CONTROL_TIMEOUT_MS),
      1_000,
    );
    let settled = false;
    let stdoutBuffer = '';
    let stderrLog = '';
    let timer = null;
    let queueIndex = 0;
    const responses = [];

    function cleanup() {
      if (timer) {
        clearTimeout(timer);
      }
      child.kill('SIGTERM');
    }

    function settleError(error) {
      if (settled) {
        return;
      }
      settled = true;
      cleanup();
      reject(error);
    }

    function settleSuccess() {
      if (settled) {
        return;
      }
      settled = true;
      cleanup();
      resolve(responses);
    }

    function resetTimer() {
      if (timer) {
        clearTimeout(timer);
      }
      timer = setTimeout(() => {
        const error = new Error('CODEX_APP_SERVER_TIMEOUT');
        error.code = 'CODEX_APP_SERVER_TIMEOUT';
        error.stderr = `Command timed out after ${timeoutMs}ms`;
        settleError(error);
      }, timeoutMs);
    }

    function sendCurrentRequest() {
      const current = queue[queueIndex];
      if (!current) {
        settleSuccess();
        return;
      }

      resetTimer();
      const resolvedParams = typeof current.params === 'function'
        ? current.params(responses)
        : current.params;
      child.stdin.write(`${JSON.stringify({
        method: current.method,
        id: current.id,
        params: resolvedParams,
      })}\n`);
    }

    function handleMessage(message) {
      const current = queue[queueIndex];
      if (!current || message?.id !== current.id) {
        return;
      }

      if (message.error) {
        const code = mapAppServerRequestError(message.error, current.errorCode);
        const error = new Error(code);
        error.code = code;
        error.stderr = JSON.stringify(message.error);
        settleError(error);
        return;
      }

      responses.push(message.result || null);
      queueIndex += 1;
      if (queueIndex >= queue.length) {
        settleSuccess();
        return;
      }

      sendCurrentRequest();
    }

    child.stdout.on('data', (chunk) => {
      stdoutBuffer += chunk.toString();
      let newlineIndex = stdoutBuffer.indexOf('\n');
      while (newlineIndex >= 0) {
        const line = stdoutBuffer.slice(0, newlineIndex).trim();
        stdoutBuffer = stdoutBuffer.slice(newlineIndex + 1);
        if (line) {
          const message = safeJsonParse(line);
          if (message) {
            handleMessage(message);
          }
        }
        newlineIndex = stdoutBuffer.indexOf('\n');
      }
    });

    child.stderr.on('data', (chunk) => {
      stderrLog += chunk.toString();
    });

    child.on('error', (error) => {
      const nextError = new Error('CODEX_PROCESS_LAUNCH_FAILED');
      nextError.code = 'CODEX_PROCESS_LAUNCH_FAILED';
      nextError.stderr = error.message;
      settleError(nextError);
    });

    child.on('close', (exitCode) => {
      if (settled) {
        return;
      }

      const current = queue[queueIndex];
      const nextError = new Error(current?.errorCode || 'CODEX_APP_SERVER_REQUEST_FAILED');
      nextError.code = current?.errorCode || 'CODEX_APP_SERVER_REQUEST_FAILED';
      nextError.stderr = stderrLog || `app-server exited with code ${exitCode ?? 'unknown'}`;
      settleError(nextError);
    });

    sendCurrentRequest();
  });
}

function buildThreadSummaryFromAppServer(thread, indexedThread = null) {
  const id = trimString(thread?.id);
  if (!id) {
    return null;
  }

  const updatedAt = unixSecondsToIso(thread?.updatedAt || thread?.createdAt) || indexedThread?.updatedAt || '';
  const threadName = trimString(thread?.name)
    || trimString(indexedThread?.threadName)
    || trimString(thread?.preview)
    || id;

  return {
    id,
    threadName,
    updatedAt,
  };
}

function isThreadActiveStatus(status) {
  if (!status || typeof status !== 'object') {
    return false;
  }

  return trimString(status.type) === 'active';
}

function buildThreadDetailFromAppServer(thread, defaults, fallback = {}) {
  const id = trimString(thread?.id);
  if (!id) {
    return null;
  }

  const messages = [];
  for (const turn of Array.isArray(thread?.turns) ? thread.turns : []) {
    for (const item of Array.isArray(turn?.items) ? turn.items : []) {
      if (!item || typeof item !== 'object') {
        continue;
      }

      if (item.type === 'userMessage') {
        const text = extractUserInputText(item.content);
        if (text) {
          appendThreadMessage(messages, {
            role: 'user',
            text,
            timestamp: unixSecondsToIso(turn?.startedAt || thread?.updatedAt),
          });
        }
        continue;
      }

      if (item.type === 'agentMessage') {
        appendThreadMessage(messages, {
          role: 'assistant',
          text: item.text,
          timestamp: unixSecondsToIso(turn?.completedAt || turn?.startedAt || thread?.updatedAt),
          phase: item.phase === 'final_answer' ? 'final' : trimString(item.phase),
        });
      }
    }
  }

  const latestUserMessage = [...messages].reverse().find((item) => item.role === 'user')?.text || '';
  const latestReply = [...messages].reverse().find((item) => item.role === 'assistant')?.text || '';
  const threadName = trimString(fallback.threadName)
    || trimString(thread?.name)
    || trimString(thread?.preview)
    || id;

  return {
    id,
    threadName,
    sessionFile: trimString(fallback.sessionFile),
    cwd: trimString(fallback.cwd) || trimString(thread?.cwd),
    model: trimString(fallback.model) || defaults.model,
    reasoningEffort: trimString(fallback.reasoningEffort) || defaults.reasoningEffort,
    updatedAt: unixSecondsToIso(thread?.updatedAt || thread?.createdAt),
    originator: trimString(thread?.source?.custom || thread?.source || ''),
    cliVersion: trimString(thread?.cliVersion),
    running: isThreadActiveStatus(thread?.status),
    latestReply,
    latestReplyTruncated: false,
    latestUserMessage,
    latestUserMessageTruncated: false,
    messages: messages.slice(-MAX_THREAD_MESSAGES),
  };
}

async function readSessionIndexThreads(fileMap, options = {}) {
  const archived = Boolean(options.archived);
  const raw = await fs.readFile(SESSION_INDEX_FILE, 'utf8').catch(() => '');
  const lines = raw.split(/\r?\n/).filter(Boolean);
  const latestById = new Map();

  for (const line of lines) {
    const item = safeJsonParse(line);
    if (!item?.id) {
      continue;
    }

    const sessionEntry = fileMap.get(item.id);
    if (!sessionEntry || sessionEntry.archived !== archived) {
      continue;
    }

    const current = latestById.get(item.id);
    const nextUpdated = Date.parse(item.updated_at || '') || 0;
    const currentUpdated = Date.parse(current?.updatedAt || '') || 0;
    if (!current || nextUpdated >= currentUpdated) {
      latestById.set(item.id, {
        id: item.id,
        threadName: trimString(item.thread_name) || item.id,
        updatedAt: item.updated_at || '',
      });
    }
  }

  return latestById;
}

async function listCodexThreadsFromSessionIndex(options = {}) {
  const { limit, archived } = parseThreadListOptions(options);
  const fileMap = await getSessionFileMap();
  const indexedThreads = await readSessionIndexThreads(fileMap, { archived });
  const threads = await Promise.all(
    Array.from(indexedThreads.values()).map(async (thread) => {
      const sessionEntry = fileMap.get(thread.id);
      const stat = sessionEntry
        ? await fs.stat(sessionEntry.path).catch(() => null)
        : null;
      const indexUpdatedAtMs = Date.parse(thread.updatedAt || '') || 0;
      const fileUpdatedAtMs = stat?.mtime ? stat.mtime.getTime() : 0;
      const resolvedUpdatedAtMs = Math.max(indexUpdatedAtMs, fileUpdatedAtMs);

      return {
        ...thread,
        updatedAt: resolvedUpdatedAtMs
          ? new Date(resolvedUpdatedAtMs).toISOString()
          : thread.updatedAt,
      };
    }),
  );

  return threads
    .sort((left, right) => (Date.parse(right.updatedAt || '') || 0) - (Date.parse(left.updatedAt || '') || 0))
    .slice(0, limit);
}

async function listCodexThreadsViaAppServer(codexExecutable, options = {}) {
  const { limit, archived } = parseThreadListOptions(options);
  const fileMap = await getSessionFileMap();
  const indexedThreads = await readSessionIndexThreads(fileMap, { archived });
  const responses = await runCodexAppServerRequests(codexExecutable, [
    {
      method: 'thread/list',
      params: {
        limit,
        archived,
        sortKey: 'updated_at',
      },
      errorCode: 'CODEX_THREAD_LIST_FAILED',
    },
  ]);

  const result = responses[1];
  const items = Array.isArray(result?.data) ? result.data : [];
  return items
    .map((thread) => buildThreadSummaryFromAppServer(
      thread,
      indexedThreads.get(trimString(thread?.id)) || null,
    ))
    .filter(Boolean)
    .slice(0, limit);
}

async function readCodexThreadFromSessionFile(sessionId, threadName = '') {
  const sessionFile = await resolveSessionFile(sessionId);
  if (!sessionFile) {
    return null;
  }

  const raw = await fs.readFile(sessionFile, 'utf8').catch(() => '');
  const stat = await fs.stat(sessionFile).catch(() => null);
  const defaults = await getCodexDefaults();

  let sessionMeta = null;
  let lastTurnContext = null;
  let lastUserMessage = '';
  let lastAssistantMessage = '';
  let running = false;
  const messages = [];

  for (const line of raw.split(/\r?\n/)) {
    if (!line.trim()) {
      continue;
    }

    const item = safeJsonParse(line);
    if (!item || typeof item !== 'object') {
      continue;
    }

    if (item.type === 'session_meta') {
      sessionMeta = item.payload || null;
      continue;
    }

    if (item.type === 'turn_context') {
      lastTurnContext = item.payload || null;
      continue;
    }

    if (item.type === 'response_item' && item.payload?.type === 'message' && item.payload?.role === 'assistant') {
      const extracted = extractContentText(item.payload?.content);
      if (extracted) {
        lastAssistantMessage = extracted;
        appendThreadMessage(messages, {
          role: 'assistant',
          text: extracted,
          timestamp: item.timestamp,
          phase: item.payload?.phase || '',
        });
      }
      continue;
    }

    if (item.type !== 'event_msg') {
      continue;
    }

    const eventType = item.payload?.type;
    if (eventType === 'user_message' && item.payload?.message) {
      lastUserMessage = String(item.payload.message).trim();
      appendThreadMessage(messages, {
        role: 'user',
        text: lastUserMessage,
        timestamp: item.timestamp,
      });
      continue;
    }

    if (eventType === 'agent_message' && item.payload?.message) {
      lastAssistantMessage = String(item.payload.message).trim();
      appendThreadMessage(messages, {
        role: 'assistant',
        text: lastAssistantMessage,
        timestamp: item.timestamp,
        phase: item.payload?.phase || '',
      });
      continue;
    }

    if (eventType === 'task_started') {
      running = true;
      continue;
    }

    if (eventType === 'task_complete') {
      running = false;
    }
  }

  const reply = trimMessage(lastAssistantMessage);
  const prompt = trimMessage(lastUserMessage, 4_000);

  return {
    id: sessionId,
    threadName: threadName || String(sessionMeta?.id || sessionId),
    sessionFile,
    cwd: lastTurnContext?.cwd || sessionMeta?.cwd || '',
    model: lastTurnContext?.model || defaults.model,
    reasoningEffort: lastTurnContext?.effort || defaults.reasoningEffort,
    updatedAt: stat?.mtime ? stat.mtime.toISOString() : '',
    originator: sessionMeta?.originator || '',
    cliVersion: sessionMeta?.cli_version || '',
    running,
    latestReply: reply.text,
    latestReplyTruncated: reply.truncated,
    latestUserMessage: prompt.text,
    latestUserMessageTruncated: prompt.truncated,
    messages: messages.slice(-MAX_THREAD_MESSAGES),
  };
}

async function readCodexThreadViaAppServer(codexExecutable, sessionId, threadFallback = {}) {
  const defaults = await getCodexDefaults();
  const responses = await runCodexAppServerRequests(codexExecutable, [
    {
      method: 'thread/read',
      params: {
        threadId: sessionId,
        includeTurns: true,
      },
      errorCode: 'CODEX_THREAD_READ_FAILED',
    },
  ]);
  const thread = responses[1]?.thread || null;
  if (!thread) {
    return null;
  }

  const sessionEntry = await resolveSessionEntry(sessionId);
  return buildThreadDetailFromAppServer(thread, defaults, {
    threadName: trimString(threadFallback?.threadName),
    sessionFile: sessionEntry?.path || '',
    model: trimString(threadFallback?.model),
    reasoningEffort: trimString(threadFallback?.reasoningEffort),
    cwd: trimString(threadFallback?.cwd),
  });
}

async function codexThreadExists(sessionId) {
  if (await resolveSessionEntry(sessionId)) {
    return true;
  }

  const codexExecutable = await resolveCodexExecutable().catch(() => '');
  if (!codexExecutable) {
    return false;
  }

  try {
    const detail = await readCodexThreadViaAppServer(codexExecutable, sessionId);
    return Boolean(detail?.id);
  } catch (error) {
    if (error?.code === 'CODEX_THREAD_NOT_FOUND') {
      return false;
    }
    throw error;
  }
}

function parseWindowsPathLines(raw) {
  return String(raw || '')
    .split(/\r?\n/)
    .map((line) => line.trim())
    .filter(Boolean)
    .filter((line) => /^[a-zA-Z]:\\/.test(line));
}

async function execFileCapture(command, args, options = {}) {
  return new Promise((resolve) => {
    const timeoutMs = Number(options.timeoutMs || 0);
    const child = spawn(command, args, {
      detached: false,
      stdio: ['ignore', 'pipe', 'pipe'],
      windowsHide: true,
    });

    let stdout = '';
    let stderr = '';

    child.stdout.on('data', (chunk) => {
      stdout += chunk.toString();
    });

    child.stderr.on('data', (chunk) => {
      stderr += chunk.toString();
    });

    let settled = false;
    let timer = null;
    if (timeoutMs > 0) {
      timer = setTimeout(() => {
        if (settled) {
          return;
        }
        settled = true;
        child.kill('SIGTERM');
        resolve({
          exitCode: 124,
          stdout,
          stderr: `${stderr}\nCommand timed out after ${timeoutMs}ms`.trim(),
        });
      }, timeoutMs);
    }

    child.on('error', (error) => {
      if (settled) {
        return;
      }
      settled = true;
      if (timer) {
        clearTimeout(timer);
      }
      resolve({
        exitCode: 1,
        stdout,
        stderr: `${stderr}\n${error.message}`.trim(),
      });
    });

    child.on('close', (exitCode) => {
      if (settled) {
        return;
      }
      settled = true;
      if (timer) {
        clearTimeout(timer);
      }
      resolve({
        exitCode: typeof exitCode === 'number' ? exitCode : 1,
        stdout,
        stderr,
      });
    });
  });
}

async function runCodexJobViaAppServer(job) {
  return new Promise((resolve, reject) => {
    const child = spawn(job.codexExecutable, ['app-server'], {
      detached: false,
      stdio: ['pipe', 'pipe', 'pipe'],
      windowsHide: true,
    });

    let settled = false;
    let stdoutBuffer = '';
    let stdoutLog = '';
    let stderrLog = '';
    let finalText = '';
    let timer = null;
    let lastTurnError = null;

    function settleError(error) {
      if (settled) {
        return;
      }
      settled = true;
      if (timer) {
        clearTimeout(timer);
      }
      child.kill('SIGTERM');
      reject(error);
    }

    async function settleSuccess() {
      if (settled) {
        return;
      }
      settled = true;
      if (timer) {
        clearTimeout(timer);
      }
      child.kill('SIGTERM');

      const latestThread = await readCodexThread(job.sessionId, job.threadName).catch(() => null);
      const finalMessage = String(latestThread?.latestReply || finalText || '').trim();
      resolve({
        stdout: stdoutLog,
        stderr: stderrLog,
        finalMessage,
      });
    }

    function sendMessage(message) {
      child.stdin.write(`${JSON.stringify(message)}\n`);
    }

    function handleAppServerMessage(message) {
      if (message?.id === 1) {
        if (message.error) {
          const error = new Error('CODEX_APP_SERVER_INIT_FAILED');
          error.code = 'CODEX_APP_SERVER_INIT_FAILED';
          error.stderr = JSON.stringify(message.error);
          settleError(error);
          return;
        }

        sendMessage({
          method: 'thread/resume',
          id: 2,
          params: {
            threadId: job.sessionId,
            persistExtendedHistory: false,
          },
        });
        return;
      }

      if (message?.id === 2) {
        if (message.error) {
          const error = new Error('CODEX_THREAD_RESUME_FAILED');
          error.code = 'CODEX_THREAD_RESUME_FAILED';
          error.stderr = JSON.stringify(message.error);
          settleError(error);
          return;
        }

        sendMessage({
          method: 'turn/start',
          id: 3,
          params: {
            threadId: job.sessionId,
            input: buildTurnInputForJob(job),
            model: job.model,
            effort: job.reasoningEffort,
          },
        });
        return;
      }

      if (message?.id === 3) {
        if (message.error) {
          const error = new Error('CODEX_TURN_START_FAILED');
          error.code = 'CODEX_TURN_START_FAILED';
          error.stderr = JSON.stringify(message.error);
          settleError(error);
        }
        return;
      }

      if (message?.method === 'item/agentMessage/delta' && message?.params?.threadId === job.sessionId) {
        finalText += String(message.params?.delta || '');
        return;
      }

      if (message?.method === 'error' && message?.params?.threadId === job.sessionId) {
        lastTurnError = message.params;
        return;
      }

      if (message?.method === 'turn/completed' && message?.params?.threadId === job.sessionId) {
        const turnStatus = String(message.params?.turn?.status || '').trim();
        if (turnStatus === 'failed') {
          const error = new Error('CODEX_TURN_FAILED');
          error.code = String(
            message.params?.turn?.error?.type
            || lastTurnError?.error?.type
            || 'CODEX_TURN_FAILED',
          );
          error.stderr = JSON.stringify(message.params?.turn?.error || lastTurnError || {});
          settleError(error);
          return;
        }

        settleSuccess();
      }
    }

    child.stdout.on('data', (chunk) => {
      const text = chunk.toString();
      stdoutLog += text;
      stdoutBuffer += text;

      let newlineIndex = stdoutBuffer.indexOf('\n');
      while (newlineIndex >= 0) {
        const line = stdoutBuffer.slice(0, newlineIndex).trim();
        stdoutBuffer = stdoutBuffer.slice(newlineIndex + 1);
        if (line) {
          try {
            handleAppServerMessage(JSON.parse(line));
          } catch {
            // Keep the raw app-server stream in eventsFile even if one line is not JSON.
          }
        }
        newlineIndex = stdoutBuffer.indexOf('\n');
      }
    });

    child.stderr.on('data', (chunk) => {
      stderrLog += chunk.toString();
    });

    child.on('error', (error) => {
      const nextError = new Error('CODEX_PROCESS_LAUNCH_FAILED');
      nextError.code = 'CODEX_PROCESS_LAUNCH_FAILED';
      nextError.stderr = error.message;
      settleError(nextError);
    });

    child.on('close', (exitCode) => {
      if (settled) {
        return;
      }

      const nextError = new Error('CODEX_TURN_FAILED');
      nextError.code = typeof exitCode === 'number' && exitCode !== 0
        ? 'CODEX_TURN_FAILED'
        : 'CODEX_TURN_STREAM_FAILED';
      nextError.stderr = lastTurnError
        ? JSON.stringify(lastTurnError)
        : stderrLog;
      settleError(nextError);
    });

    timer = setTimeout(() => {
      const error = new Error('CODEX_TURN_TIMEOUT');
      error.code = 'CODEX_TURN_TIMEOUT';
      error.stderr = `Command timed out after ${CODEX_EXEC_TIMEOUT_MS}ms`;
      settleError(error);
    }, CODEX_EXEC_TIMEOUT_MS);

    sendMessage({
      method: 'initialize',
      id: 1,
      params: {
        clientInfo: {
          name: 'home-mobile-bridge',
          title: 'home mobile bridge',
          version: '0.0.1',
        },
        capabilities: null,
      },
    });
  });
}

async function discoverDesktopCodexSourceFromProcessList() {
  if (process.platform !== 'win32') {
    return '';
  }

  const result = await execFileCapture('powershell.exe', [
    '-NoProfile',
    '-Command',
    "$paths = Get-CimInstance Win32_Process | Where-Object { $_.Name -match '^(?i:codex\\.exe)$' } | ForEach-Object { $_.ExecutablePath } | Where-Object { $_ }; $paths | ForEach-Object { $_ }",
  ]);
  if (result.exitCode !== 0) {
    return '';
  }

  const seen = new Set();
  const candidates = [];
  for (const executablePath of parseWindowsPathLines(result.stdout)) {
    if (/\\resources\\codex\.exe$/i.test(executablePath)) {
      candidates.push(executablePath);
      continue;
    }

    if (/\\app\\codex\.exe$/i.test(executablePath)) {
      candidates.push(path.join(path.dirname(executablePath), 'resources', 'codex.exe'));
    }
  }

  for (const candidate of candidates) {
    const normalized = path.normalize(candidate);
    if (seen.has(normalized)) {
      continue;
    }
    seen.add(normalized);
    if (await pathExists(normalized)) {
      return normalized;
    }
  }

  return '';
}

async function ensureDesktopCodexExecutableCopy() {
  const sourcePath = await discoverDesktopCodexSourceFromProcessList();
  if (!sourcePath) {
    return '';
  }

  await ensureControlStateDirs();
  const [sourceStat, targetStat] = await Promise.all([
    fs.stat(sourcePath).catch(() => null),
    fs.stat(DESKTOP_CODEX_COPY_PATH).catch(() => null),
  ]);
  if (!sourceStat) {
    return '';
  }

  const needsRefresh = !targetStat
    || targetStat.size !== sourceStat.size
    || targetStat.mtimeMs < sourceStat.mtimeMs;
  if (needsRefresh) {
    await fs.copyFile(sourcePath, DESKTOP_CODEX_COPY_PATH);
  }

  return DESKTOP_CODEX_COPY_PATH;
}

async function resolveCodexExecutable() {
  const explicit = process.env.CODEX_CLI_PATH?.trim() || '';
  const candidates = [];

  if (explicit) {
    candidates.push(explicit);
  } else {
    const desktopCopy = await ensureDesktopCodexExecutableCopy().catch(() => '');
    if (desktopCopy) {
      candidates.push(desktopCopy);
    }

    if (process.platform === 'win32') {
      candidates.push(
        path.join(CODEX_HOME, '.sandbox-bin', 'codex.exe'),
        path.join(CODEX_HOME, '.sandbox-bin', 'codex'),
      );
    } else {
      candidates.push(
        path.join(CODEX_HOME, '.sandbox-bin', 'codex'),
        path.join(CODEX_HOME, '.sandbox-bin', 'codex.exe'),
      );
    }
  }

  for (const candidate of candidates) {
    if (candidate && await pathExists(candidate)) {
      return candidate;
    }
  }

  return '';
}

export async function getCodexDefaults() {
  const raw = await fs.readFile(CONFIG_FILE, 'utf8').catch(() => '');
  const modelMatch = raw.match(/^model\s*=\s*"([^"]+)"/m);
  const effortMatch = raw.match(/^model_reasoning_effort\s*=\s*"([^"]+)"/m);

  return {
    model: modelMatch?.[1] || 'gpt-5.4',
    reasoningEffort: effortMatch?.[1] || 'medium',
  };
}

export async function getCodexModels() {
  const payload = await readJsonFile(MODELS_CACHE_FILE, { models: [] });
  return (payload?.models || [])
    .filter((item) => item && item.visibility !== 'hidden')
    .map((item) => ({
      slug: item.slug,
      displayName: item.display_name || item.slug,
      description: item.description || '',
      defaultReasoningLevel: item.default_reasoning_level || 'medium',
      supportedReasoningLevels: Array.isArray(item.supported_reasoning_levels)
        ? item.supported_reasoning_levels.map((level) => ({
          effort: level.effort,
          description: level.description || '',
        }))
        : [],
    }));
}

export async function listCodexThreads(limitOrOptions = MAX_THREADS) {
  const options = parseThreadListOptions(limitOrOptions);
  let threads = [];
  const codexExecutable = await resolveCodexExecutable().catch(() => '');
  if (codexExecutable) {
    try {
      threads = await listCodexThreadsViaAppServer(codexExecutable, options);
    } catch {
      // Fall back to session index so the mobile shell can still render historical threads.
    }
  }

  if (!threads.length) {
    threads = await listCodexThreadsFromSessionIndex(options);
  }

  if (options.archived) {
    return threads;
  }

  const pendingThreads = await readPendingThreadRegistry();
  const nextById = new Map(threads.map((item) => [item.id, item]));
  let stalePendingFound = false;
  for (const pending of pendingThreads) {
    if (nextById.has(pending.id)) {
      stalePendingFound = true;
      continue;
    }
    nextById.set(pending.id, pending);
  }

  if (stalePendingFound) {
    await writePendingThreadRegistry(
      pendingThreads.filter((item) => !threads.some((thread) => thread.id === item.id)),
    );
  }

  return Array.from(nextById.values())
    .sort((left, right) => (Date.parse(right.updatedAt || '') || 0) - (Date.parse(left.updatedAt || '') || 0))
    .slice(0, options.limit);
}

export async function readCodexThread(sessionId, threadName = '') {
  const fileDetail = await readCodexThreadFromSessionFile(sessionId, threadName);
  if (fileDetail) {
    return fileDetail;
  }

  const codexExecutable = await resolveCodexExecutable().catch(() => '');
  if (!codexExecutable) {
    return null;
  }

  try {
    const pendingThread = (await readPendingThreadRegistry()).find((item) => item.id === sessionId) || null;
    return await readCodexThreadViaAppServer(
      codexExecutable,
      sessionId,
      {
        threadName: threadName || pendingThread?.threadName || '',
        model: pendingThread?.model || '',
        reasoningEffort: pendingThread?.reasoningEffort || '',
        cwd: pendingThread?.cwd || '',
      },
    );
  } catch (error) {
    if (error?.code === 'CODEX_THREAD_NOT_FOUND') {
      return null;
    }
    throw error;
  }
}

export async function getActiveCodexJob() {
  return clearFinishedActiveJob();
}

export async function readCodexJob(jobId) {
  const job = await readJobRecord(jobId);
  return sanitizeJob(job);
}

function resolveReasoningEffort(modelEntry, requestedEffort, defaultEffort) {
  const supported = new Set((modelEntry?.supportedReasoningLevels || []).map((item) => item.effort));
  if (requestedEffort && supported.has(requestedEffort)) {
    return requestedEffort;
  }
  if (defaultEffort && supported.has(defaultEffort)) {
    return defaultEffort;
  }
  return modelEntry?.defaultReasoningLevel || defaultEffort || 'medium';
}

export async function createCodexThread({
  model,
  reasoningEffort,
  cwd,
  threadName,
} = {}) {
  const codexExecutable = await resolveCodexExecutable();
  if (!codexExecutable) {
    const error = new Error('CODEX_CLI_NOT_FOUND');
    error.code = 'CODEX_CLI_NOT_FOUND';
    throw error;
  }

  const models = await getCodexModels();
  const defaults = await getCodexDefaults();
  const resolvedModel = trimString(model) || defaults.model;
  const modelEntry = models.find((item) => item.slug === resolvedModel) || null;
  if (models.length && !modelEntry) {
    const error = new Error('CODEX_MODEL_INVALID');
    error.code = 'CODEX_MODEL_INVALID';
    throw error;
  }

  const resolvedReasoningEffort = resolveReasoningEffort(
    modelEntry,
    trimString(reasoningEffort),
    defaults.reasoningEffort,
  );
  const resolvedCwd = trimString(cwd) || REPO_ROOT;
  const nextThreadName = trimString(threadName);
  const requests = [
    {
      method: 'thread/start',
      params: {
        model: resolvedModel,
        cwd: resolvedCwd,
        config: {
          model_reasoning_effort: resolvedReasoningEffort,
        },
        experimentalRawEvents: false,
        persistExtendedHistory: false,
        sessionStartSource: 'startup',
      },
      errorCode: 'CODEX_THREAD_CREATE_FAILED',
    },
  ];

  if (nextThreadName) {
    requests.push({
      method: 'thread/name/set',
      params: (responses) => ({
        threadId: responses[1]?.thread?.id || '',
        name: nextThreadName,
      }),
      errorCode: 'CODEX_THREAD_NAME_SET_FAILED',
    });
  }

  let responses;
  try {
    responses = await runCodexAppServerRequests(codexExecutable, requests);
  } catch (error) {
    if (error?.code === 'CODEX_THREAD_NAME_SET_FAILED') {
      throw error;
    }
    const nextError = new Error(error?.code || 'CODEX_THREAD_CREATE_FAILED');
    nextError.code = error?.code || 'CODEX_THREAD_CREATE_FAILED';
    throw nextError;
  }

  invalidateThreadFileCache();
  const createdThread = responses[1]?.thread || null;
  const summary = buildThreadSummaryFromAppServer(createdThread, null);
  if (!summary) {
    const error = new Error('CODEX_THREAD_CREATE_FAILED');
    error.code = 'CODEX_THREAD_CREATE_FAILED';
    throw error;
  }

  const createdSummary = {
    ...summary,
    threadName: nextThreadName || summary.threadName,
    cwd: resolvedCwd,
    model: resolvedModel,
    reasoningEffort: resolvedReasoningEffort,
  };
  await upsertPendingThread(createdSummary);

  return {
    ...createdSummary,
  };
}

export async function archiveCodexThread(threadId) {
  const nextThreadId = trimString(threadId);
  if (!nextThreadId) {
    const error = new Error('CODEX_THREAD_NOT_FOUND');
    error.code = 'CODEX_THREAD_NOT_FOUND';
    throw error;
  }

  const codexExecutable = await resolveCodexExecutable();
  if (!codexExecutable) {
    const error = new Error('CODEX_CLI_NOT_FOUND');
    error.code = 'CODEX_CLI_NOT_FOUND';
    throw error;
  }

  const sessionEntry = await resolveSessionEntry(nextThreadId);
  if (sessionEntry && !sessionEntry.archived) {
    const stat = await fs.stat(sessionEntry.path).catch(() => null);
    if (stat && stat.size === 0) {
      const error = new Error('CODEX_THREAD_ARCHIVE_REQUIRES_HISTORY');
      error.code = 'CODEX_THREAD_ARCHIVE_REQUIRES_HISTORY';
      throw error;
    }
  }

  await runCodexAppServerRequests(codexExecutable, [
    {
      method: 'thread/archive',
      params: {
        threadId: nextThreadId,
      },
      errorCode: 'CODEX_THREAD_ARCHIVE_FAILED',
    },
  ]);
  invalidateThreadFileCache();
  await removePendingThread(nextThreadId);

  return {
    id: nextThreadId,
    archivedAt: nowIso(),
  };
}

export async function unarchiveCodexThread(threadId) {
  const nextThreadId = trimString(threadId);
  if (!nextThreadId) {
    const error = new Error('CODEX_THREAD_NOT_FOUND');
    error.code = 'CODEX_THREAD_NOT_FOUND';
    throw error;
  }

  const codexExecutable = await resolveCodexExecutable();
  if (!codexExecutable) {
    const error = new Error('CODEX_CLI_NOT_FOUND');
    error.code = 'CODEX_CLI_NOT_FOUND';
    throw error;
  }

  await runCodexAppServerRequests(codexExecutable, [
    {
      method: 'thread/unarchive',
      params: {
        threadId: nextThreadId,
      },
      errorCode: 'CODEX_THREAD_UNARCHIVE_FAILED',
    },
  ]);
  invalidateThreadFileCache();
  await removePendingThread(nextThreadId);

  return {
    id: nextThreadId,
    unarchivedAt: nowIso(),
  };
}

export async function queueCodexResumeJob({
  sessionId,
  prompt,
  model,
  reasoningEffort,
  threadName,
  attachments,
}) {
  const trimmedPrompt = String(prompt || '').trim();
  if (!trimmedPrompt) {
    const error = new Error('CODEX_PROMPT_REQUIRED');
    error.code = 'CODEX_PROMPT_REQUIRED';
    throw error;
  }

  const codexExecutable = await resolveCodexExecutable();
  if (!codexExecutable) {
    const error = new Error('CODEX_CLI_NOT_FOUND');
    error.code = 'CODEX_CLI_NOT_FOUND';
    throw error;
  }

  if (!(await codexThreadExists(sessionId))) {
    const error = new Error('CODEX_THREAD_NOT_FOUND');
    error.code = 'CODEX_THREAD_NOT_FOUND';
    throw error;
  }

  const activeJob = await getActiveCodexJob();
  if (activeJob && ['queued', 'running'].includes(activeJob.status)) {
    const error = new Error('CODEX_JOB_ALREADY_RUNNING');
    error.code = 'CODEX_JOB_ALREADY_RUNNING';
    error.activeJob = activeJob;
    throw error;
  }

  const models = await getCodexModels();
  const defaults = await getCodexDefaults();
  const resolvedModel = String(model || defaults.model || '').trim();
  const modelEntry = models.find((item) => item.slug === resolvedModel) || null;
  if (!modelEntry) {
    const error = new Error('CODEX_MODEL_INVALID');
    error.code = 'CODEX_MODEL_INVALID';
    throw error;
  }

  const resolvedReasoningEffort = resolveReasoningEffort(modelEntry, reasoningEffort, defaults.reasoningEffort);
  const resolvedAttachments = await resolveQueuedAttachments(attachments);
  const jobId = randomUUID();
  const job = {
    id: jobId,
    sessionId,
    threadName: String(threadName || sessionId),
    prompt: trimmedPrompt,
    model: resolvedModel,
    reasoningEffort: resolvedReasoningEffort,
    status: 'queued',
    createdAt: nowIso(),
    startedAt: '',
    finishedAt: '',
    exitCode: null,
    finalMessage: '',
    error: '',
    outputFile: getJobReplyPath(jobId),
    eventsFile: getJobEventsPath(jobId),
    codexExecutable,
    attachments: resolvedAttachments,
  };

  await writeJobRecord(jobId, job);

  const state = await readRuntimeState();
  await writeRuntimeState({
    ...state,
    activeJobId: jobId,
  });

  const child = spawn(process.execPath, [RUNNER_SCRIPT, jobId], {
    detached: true,
    stdio: 'ignore',
    windowsHide: true,
  });
  child.unref();

  return sanitizeJob(job);
}

export async function sendCodexDesktopAppMessage(prompt) {
  const trimmedPrompt = String(prompt || '').trim();
  if (!trimmedPrompt) {
    const error = new Error('CODEX_PROMPT_REQUIRED');
    error.code = 'CODEX_PROMPT_REQUIRED';
    throw error;
  }

  const codexExecutable = await resolveCodexExecutable();
  if (!codexExecutable) {
    const error = new Error('CODEX_CLI_NOT_FOUND');
    error.code = 'CODEX_CLI_NOT_FOUND';
    throw error;
  }

  const result = await execFileCapture(codexExecutable, [
    'debug',
    'app-server',
    'send-message-v2',
    trimmedPrompt,
  ], {
    timeoutMs: CODEX_APP_SERVER_SEND_TIMEOUT_MS,
  });

  if (result.exitCode !== 0) {
    const stderr = String(result.stderr || '').trim();
    const code = result.exitCode === 124
      ? 'CODEX_APP_SERVER_SEND_TIMEOUT'
      : (stderr || 'CODEX_APP_SERVER_SEND_FAILED');
    const error = new Error(code);
    error.code = code;
    error.stderr = stderr;
    throw error;
  }

  return {
    acceptedAt: nowIso(),
  };
}

export async function runQueuedCodexJob(jobId) {
  const job = await readJobRecord(jobId);
  if (!job) {
    return null;
  }

  const startedAt = nowIso();
  await writeJobRecord(jobId, {
    ...job,
    status: 'running',
    startedAt,
    error: '',
  });

  let nextStatus = 'succeeded';
  let nextError = '';
  let finalMessage = '';
  let eventsOutput = '';

  try {
    const result = await runCodexJobViaAppServer(job);
    finalMessage = result.finalMessage || '';
    eventsOutput = result.stdout || '';
  } catch (error) {
    nextStatus = 'failed';
    nextError = normalizeAppServerError(error);
    eventsOutput = String(error?.stderr || '');
  }

  await fs.writeFile(job.eventsFile, eventsOutput, 'utf8').catch(() => undefined);
  await fs.writeFile(job.outputFile, finalMessage, 'utf8').catch(() => undefined);

  const completedJob = {
    ...job,
    status: nextStatus,
    startedAt,
    finishedAt: nowIso(),
    exitCode: nextStatus === 'succeeded' ? 0 : 1,
    finalMessage: String(finalMessage || '').trim(),
    error: nextError,
  };

  await writeJobRecord(jobId, completedJob);

  const state = await readRuntimeState();
  if (state.activeJobId === jobId) {
    await writeRuntimeState({
      ...state,
      activeJobId: '',
    });
  }

  await removePendingThread(job.sessionId);

  return sanitizeJob(completedJob);
}

export async function getCodexPageBootstrap(limit = MAX_THREADS) {
  const [threads, defaults, models, activeJob] = await Promise.all([
    listCodexThreads(limit),
    getCodexDefaults(),
    getCodexModels(),
    getActiveCodexJob(),
  ]);

  const selectedThreadId = activeJob?.sessionId || threads[0]?.id || '';
  const selectedThread = selectedThreadId
    ? await readCodexThread(
      selectedThreadId,
      threads.find((item) => item.id === selectedThreadId)?.threadName || '',
    )
    : null;

  return {
    threads,
    defaults,
    models,
    activeJob,
    selectedThread,
    selectedThreadId,
    bridgeStatus: {
      mode: 'local',
      reachable: true,
      error: '',
    },
  };
}
