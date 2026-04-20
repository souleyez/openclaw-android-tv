import http from 'node:http';
import { URL } from 'node:url';
import {
  archiveCodexThread,
  createCodexThread,
  getActiveCodexJob,
  getCodexDefaults,
  getCodexModels,
  getCodexPageBootstrap,
  listCodexThreads,
  queueCodexResumeJob,
  readCodexJob,
  readCodexThread,
} from '../lib/codex-runtime-control.mjs';

const BRIDGE_TOKEN = String(process.env.CODEX_BRIDGE_TOKEN || '').trim();
const HOST = String(process.env.CODEX_BRIDGE_HOST || '127.0.0.1').trim() || '127.0.0.1';
const PORT = Number(process.env.CODEX_BRIDGE_PORT || 33980);

function sendJson(response, statusCode, payload) {
  response.writeHead(statusCode, {
    'Content-Type': 'application/json; charset=utf-8',
    'Cache-Control': 'no-store',
  });
  response.end(JSON.stringify(payload));
}

function sendError(response, statusCode, code, extra = {}) {
  sendJson(response, statusCode, {
    error: code,
    ...extra,
  });
}

async function readJsonBody(request) {
  const chunks = [];

  for await (const chunk of request) {
    chunks.push(Buffer.from(chunk));
  }

  if (!chunks.length) {
    return {};
  }

  try {
    return JSON.parse(Buffer.concat(chunks).toString('utf8'));
  } catch {
    const error = new Error('CODEX_BRIDGE_BODY_INVALID');
    error.code = 'CODEX_BRIDGE_BODY_INVALID';
    throw error;
  }
}

function authorize(request) {
  if (!BRIDGE_TOKEN) {
    return true;
  }

  const header = request.headers['x-codex-bridge-token'];
  return String(Array.isArray(header) ? header[0] : header || '').trim() === BRIDGE_TOKEN;
}

function routeMatch(pathname, pattern) {
  const pathParts = pathname.split('/').filter(Boolean);
  const patternParts = pattern.split('/').filter(Boolean);
  if (pathParts.length !== patternParts.length) {
    return null;
  }

  const params = {};
  for (let index = 0; index < patternParts.length; index += 1) {
    const patternPart = patternParts[index];
    const pathPart = pathParts[index];
    if (patternPart.startsWith(':')) {
      params[patternPart.slice(1)] = decodeURIComponent(pathPart);
      continue;
    }
    if (patternPart !== pathPart) {
      return null;
    }
  }

  return params;
}

async function handleRequest(request, response) {
  if (!authorize(request)) {
    sendError(response, 401, 'CODEX_BRIDGE_UNAUTHORIZED');
    return;
  }

  const url = new URL(request.url || '/', `http://${request.headers.host || '127.0.0.1'}`);
  const { pathname, searchParams } = url;

  if (request.method === 'GET' && pathname === '/bridge/health') {
    const activeJob = await getActiveCodexJob();
    sendJson(response, 200, {
      status: 'ok',
      activeJob,
    });
    return;
  }

  if (request.method === 'GET' && pathname === '/bridge/defaults') {
    sendJson(response, 200, {
      item: await getCodexDefaults(),
    });
    return;
  }

  if (request.method === 'GET' && pathname === '/bridge/models') {
    sendJson(response, 200, {
      items: await getCodexModels(),
    });
    return;
  }

  if (request.method === 'GET' && pathname === '/bridge/threads') {
    const limit = Number(searchParams.get('limit') || '') || undefined;
    const archived = searchParams.get('archived') === '1';
    sendJson(response, 200, {
      items: await listCodexThreads({
        limit,
        archived,
      }),
    });
    return;
  }

  if (request.method === 'POST' && pathname === '/bridge/threads') {
    try {
      const body = await readJsonBody(request);
      const item = await createCodexThread({
        model: typeof body?.model === 'string' ? body.model : '',
        reasoningEffort: typeof body?.reasoningEffort === 'string' ? body.reasoningEffort : '',
        cwd: typeof body?.cwd === 'string' ? body.cwd : '',
        threadName: typeof body?.threadName === 'string' ? body.threadName : '',
      });
      sendJson(response, 201, { item });
    } catch (error) {
      const code = error?.code || (error instanceof Error ? error.message : 'CODEX_THREAD_CREATE_FAILED');
      const status = code === 'CODEX_MODEL_INVALID'
        ? 400
        : code === 'CODEX_CLI_NOT_FOUND'
          ? 503
          : 500;
      sendError(response, status, code);
    }
    return;
  }

  const threadParams = routeMatch(pathname, '/bridge/threads/:threadId');
  if (request.method === 'GET' && threadParams) {
    const item = await readCodexThread(
      threadParams.threadId,
      searchParams.get('threadName') || '',
    );
    if (!item) {
      sendError(response, 404, 'CODEX_THREAD_NOT_FOUND');
      return;
    }
    sendJson(response, 200, { item });
    return;
  }

  if (request.method === 'DELETE' && threadParams) {
    try {
      const item = await archiveCodexThread(threadParams.threadId);
      sendJson(response, 200, { item });
    } catch (error) {
      const code = error?.code || (error instanceof Error ? error.message : 'CODEX_THREAD_ARCHIVE_FAILED');
      const status = code === 'CODEX_THREAD_NOT_FOUND'
        ? 404
        : code === 'CODEX_THREAD_ARCHIVE_REQUIRES_HISTORY'
          ? 409
        : code === 'CODEX_CLI_NOT_FOUND'
          ? 503
          : 500;
      sendError(response, status, code);
    }
    return;
  }

  const threadJobParams = routeMatch(pathname, '/bridge/threads/:threadId/jobs');
  if (request.method === 'POST' && threadJobParams) {
    try {
      const body = await readJsonBody(request);
      const item = await queueCodexResumeJob({
        sessionId: threadJobParams.threadId,
        prompt: typeof body?.prompt === 'string' ? body.prompt : '',
        model: typeof body?.model === 'string' ? body.model : '',
        reasoningEffort: typeof body?.reasoningEffort === 'string' ? body.reasoningEffort : '',
        threadName: typeof body?.threadName === 'string' ? body.threadName : '',
        attachments: Array.isArray(body?.attachments)
          ? body.attachments.map((attachment) => ({
            id: typeof attachment?.id === 'string' ? attachment.id : '',
            filename: typeof attachment?.filename === 'string' ? attachment.filename : '',
            contentType: typeof attachment?.contentType === 'string' ? attachment.contentType : '',
            mediaType: typeof attachment?.mediaType === 'string' ? attachment.mediaType : '',
            size: typeof attachment?.size === 'number' ? attachment.size : 0,
            localPath: typeof attachment?.localPath === 'string' ? attachment.localPath : '',
          })).filter((attachment) => attachment.localPath)
          : [],
      });
      sendJson(response, 202, { item });
    } catch (error) {
      const code = error?.code || (error instanceof Error ? error.message : 'CODEX_JOB_CREATE_FAILED');
      const status = code === 'CODEX_JOB_ALREADY_RUNNING'
        ? 409
        : code === 'CODEX_THREAD_NOT_FOUND'
          || code === 'CODEX_MODEL_INVALID'
          || code === 'CODEX_PROMPT_REQUIRED'
          || code === 'CODEX_UPLOAD_NOT_FOUND'
          || code === 'CODEX_ATTACHMENTS_TOO_MANY'
          ? 400
          : 500;
      sendError(response, status, code, {
        activeJob: error?.activeJob || null,
      });
    }
    return;
  }

  if (request.method === 'GET' && pathname === '/bridge/jobs/active') {
    sendJson(response, 200, {
      item: await getActiveCodexJob(),
    });
    return;
  }

  const jobParams = routeMatch(pathname, '/bridge/jobs/:jobId');
  if (request.method === 'GET' && jobParams) {
    const item = await readCodexJob(jobParams.jobId);
    if (!item) {
      sendError(response, 404, 'CODEX_JOB_NOT_FOUND');
      return;
    }
    sendJson(response, 200, {
      item,
      activeJob: await getActiveCodexJob(),
    });
    return;
  }

  if (request.method === 'GET' && pathname === '/bridge/bootstrap') {
    const limit = Number(searchParams.get('limit') || '') || undefined;
    sendJson(response, 200, {
      item: await getCodexPageBootstrap(limit),
    });
    return;
  }

  sendError(response, 404, 'CODEX_BRIDGE_ROUTE_NOT_FOUND');
}

const server = http.createServer((request, response) => {
  handleRequest(request, response).catch((error) => {
    sendError(response, 500, error?.code || error?.message || 'CODEX_BRIDGE_INTERNAL');
  });
});

server.listen(PORT, HOST, () => {
  process.stdout.write(`codex-bridge listening on http://${HOST}:${PORT}\n`);
});
