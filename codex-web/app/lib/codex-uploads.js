import { createHash, randomUUID } from 'node:crypto';
import fs from 'node:fs/promises';
import path from 'node:path';

const DEFAULT_UPLOADS_ROOT = process.platform === 'win32'
  ? path.join(process.cwd(), '.storage', 'codex-uploads')
  : '/srv/home/.storage/codex-uploads';

const UPLOADS_ROOT = String(process.env.CODEX_UPLOADS_ROOT || DEFAULT_UPLOADS_ROOT).trim() || DEFAULT_UPLOADS_ROOT;
const UPLOADS_FILES_DIR = path.join(UPLOADS_ROOT, 'files');
const UPLOADS_INDEX_FILE = path.join(UPLOADS_ROOT, 'index.json');
const MAX_UPLOAD_BYTES = Math.max(Number(process.env.CODEX_UPLOAD_MAX_BYTES || 25 * 1024 * 1024), 1_024_000);
const MAX_JOB_ATTACHMENTS = Math.max(Number(process.env.CODEX_MAX_JOB_ATTACHMENTS || 8), 1);
const UPLOAD_ID_PATTERN = /^[A-Za-z0-9][A-Za-z0-9._-]{0,127}$/;

const IMAGE_EXTENSIONS = new Set(['.png', '.jpg', '.jpeg', '.webp', '.gif']);
const FILE_EXTENSIONS = new Set([
  '.md',
  '.txt',
  '.pdf',
  '.ppt',
  '.pptx',
  '.doc',
  '.docx',
  '.xls',
  '.xlsx',
  '.csv',
  '.json',
  '.zip',
]);

function trimString(value) {
  return typeof value === 'string' ? value.trim() : '';
}

function normalizeNumber(value) {
  const parsed = Number(value);
  if (!Number.isFinite(parsed) || parsed < 0) {
    return 0;
  }

  return parsed;
}

function sanitizeFilename(value) {
  const filename = path.basename(trimString(value)) || 'upload';
  return filename.replace(/[<>:"/\\|?*\u0000-\u001f]+/g, '-').trim() || 'upload';
}

function normalizeUploadId(value) {
  const uploadId = trimString(value);
  return UPLOAD_ID_PATTERN.test(uploadId) ? uploadId : '';
}

function resolveContentType(value, fallback = 'application/octet-stream') {
  const contentType = trimString(value).toLowerCase();
  return contentType || fallback;
}

function inferMediaType(contentType, filename) {
  const normalizedContentType = resolveContentType(contentType, '');
  if (normalizedContentType.startsWith('image/')) {
    return 'image';
  }

  const extension = path.extname(filename).toLowerCase();
  if (IMAGE_EXTENSIONS.has(extension)) {
    return 'image';
  }
  if (FILE_EXTENSIONS.has(extension)) {
    return 'file';
  }

  return '';
}

function normalizeUploadItem(item, { includeLocalPath = false } = {}) {
  if (!item || typeof item !== 'object') {
    return null;
  }

  const id = normalizeUploadId(item.id);
  const filename = sanitizeFilename(item.filename);
  const mediaType = trimString(item.mediaType);
  const localPath = trimString(item.localPath);

  if (!id || !filename || !['image', 'file'].includes(mediaType)) {
    return null;
  }

  const normalized = {
    id,
    filename,
    contentType: resolveContentType(item.contentType),
    mediaType,
    size: normalizeNumber(item.size),
    sha256: trimString(item.sha256),
    createdAt: trimString(item.createdAt),
    createdBy: trimString(item.createdBy),
  };

  if (includeLocalPath && localPath) {
    normalized.localPath = localPath;
  }

  return normalized;
}

function sortUploads(items) {
  return [...items].sort((left, right) => {
    const rightTime = Date.parse(right.createdAt || '') || 0;
    const leftTime = Date.parse(left.createdAt || '') || 0;
    return rightTime - leftTime;
  });
}

async function ensureUploadsStorage() {
  await fs.mkdir(UPLOADS_FILES_DIR, { recursive: true });
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
  const tempPath = `${filePath}.${process.pid}.${Date.now()}.tmp`;
  await fs.writeFile(tempPath, JSON.stringify(payload, null, 2), 'utf8');
  await fs.rename(tempPath, filePath);
}

async function sha256Buffer(buffer) {
  return createHash('sha256').update(buffer).digest('hex');
}

export function getCodexUploadsRoot() {
  return UPLOADS_ROOT;
}

export function getCodexUploadsIndexFile() {
  return UPLOADS_INDEX_FILE;
}

export function getCodexUploadsFilesDir() {
  return UPLOADS_FILES_DIR;
}

export function getCodexMaxJobAttachments() {
  return MAX_JOB_ATTACHMENTS;
}

export function sanitizeCodexUploadFilename(value) {
  return sanitizeFilename(value);
}

export function buildCodexUploadStorageName(item) {
  const uploadId = normalizeUploadId(item?.id);
  if (!uploadId) {
    return '';
  }

  return `${uploadId}-${sanitizeFilename(item?.filename || 'upload')}`;
}

export function resolveCodexUploadFilePath(item) {
  const storageName = buildCodexUploadStorageName(item);
  return storageName ? path.join(UPLOADS_FILES_DIR, storageName) : '';
}

export async function readCodexUploadsManifest() {
  await ensureUploadsStorage();
  const payload = await readJsonFile(UPLOADS_INDEX_FILE, []);
  if (!Array.isArray(payload)) {
    return [];
  }

  return sortUploads(payload.map((item) => normalizeUploadItem(item)).filter(Boolean));
}

export async function writeCodexUploadsManifest(items) {
  await ensureUploadsStorage();
  const normalized = sortUploads(
    Array.isArray(items) ? items.map((item) => normalizeUploadItem(item)).filter(Boolean) : [],
  );
  await writeJsonFileAtomic(UPLOADS_INDEX_FILE, normalized);
  return normalized;
}

export async function upsertCodexUpload(item) {
  const normalized = normalizeUploadItem(item);
  if (!normalized) {
    const error = new Error('CODEX_UPLOAD_INVALID');
    error.code = 'CODEX_UPLOAD_INVALID';
    throw error;
  }

  const current = await readCodexUploadsManifest();
  const next = [
    normalized,
    ...current.filter((entry) => entry.id !== normalized.id),
  ];
  await writeCodexUploadsManifest(next);
  return normalized;
}

export async function storeCodexUpload(file, { createdBy = '' } = {}) {
  const filename = sanitizeFilename(file?.name || '');
  if (!filename) {
    const error = new Error('CODEX_UPLOAD_REQUIRED');
    error.code = 'CODEX_UPLOAD_REQUIRED';
    throw error;
  }

  const size = normalizeNumber(file?.size);
  if (!size) {
    const error = new Error('CODEX_UPLOAD_REQUIRED');
    error.code = 'CODEX_UPLOAD_REQUIRED';
    throw error;
  }
  if (size > MAX_UPLOAD_BYTES) {
    const error = new Error('CODEX_UPLOAD_FILE_TOO_LARGE');
    error.code = 'CODEX_UPLOAD_FILE_TOO_LARGE';
    throw error;
  }

  const contentType = resolveContentType(file?.type);
  const mediaType = inferMediaType(contentType, filename);
  if (!mediaType) {
    const error = new Error('CODEX_UPLOAD_FILE_TYPE_INVALID');
    error.code = 'CODEX_UPLOAD_FILE_TYPE_INVALID';
    throw error;
  }

  const arrayBuffer = await file.arrayBuffer();
  const buffer = Buffer.from(arrayBuffer);
  const id = randomUUID();
  const createdAt = new Date().toISOString();
  const item = {
    id,
    filename,
    contentType,
    mediaType,
    size: buffer.length,
    sha256: await sha256Buffer(buffer),
    createdAt,
    createdBy: trimString(createdBy),
  };

  await ensureUploadsStorage();
  const filePath = resolveCodexUploadFilePath(item);
  await fs.writeFile(filePath, buffer);
  await upsertCodexUpload(item);
  return item;
}

export async function readCodexUpload(uploadId) {
  const id = normalizeUploadId(uploadId);
  if (!id) {
    return null;
  }

  const items = await readCodexUploadsManifest();
  return items.find((item) => item.id === id) || null;
}

export async function readCodexUploadFile(uploadId) {
  const item = await readCodexUpload(uploadId);
  if (!item) {
    return null;
  }

  const filePath = resolveCodexUploadFilePath(item);
  const stat = await fs.stat(filePath).catch(() => null);
  if (!stat?.isFile()) {
    return null;
  }

  return {
    item: {
      ...item,
      size: item.size || stat.size,
    },
    filePath,
    stat,
  };
}

export async function resolveCodexUploadSelections(attachments) {
  const items = Array.isArray(attachments) ? attachments : [];
  if (items.length > MAX_JOB_ATTACHMENTS) {
    const error = new Error('CODEX_ATTACHMENTS_TOO_MANY');
    error.code = 'CODEX_ATTACHMENTS_TOO_MANY';
    throw error;
  }

  const resolved = [];
  const seen = new Set();
  for (const item of items) {
    const uploadId = normalizeUploadId(item?.id);
    if (!uploadId || seen.has(uploadId)) {
      continue;
    }
    seen.add(uploadId);

    const upload = await readCodexUpload(uploadId);
    if (!upload) {
      const error = new Error('CODEX_UPLOAD_NOT_FOUND');
      error.code = 'CODEX_UPLOAD_NOT_FOUND';
      throw error;
    }

    resolved.push(upload);
  }

  return resolved;
}

export async function resolveCodexUploadSelectionsForLocalExecution(attachments) {
  const resolved = await resolveCodexUploadSelections(attachments);
  const output = [];

  for (const item of resolved) {
    const file = await readCodexUploadFile(item.id);
    if (!file) {
      const error = new Error('CODEX_UPLOAD_NOT_FOUND');
      error.code = 'CODEX_UPLOAD_NOT_FOUND';
      throw error;
    }

    output.push({
      ...item,
      localPath: file.filePath,
      size: item.size || file.stat.size,
    });
  }

  return output;
}
