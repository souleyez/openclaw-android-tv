import fs from 'node:fs/promises';
import path from 'node:path';

const DEFAULT_DOWNLOADS_ROOT = process.platform === 'win32'
  ? path.join(process.cwd(), '.storage', 'codex-downloads')
  : '/srv/home/.storage/codex-downloads';

const DOWNLOADS_ROOT = String(process.env.CODEX_DOWNLOADS_ROOT || DEFAULT_DOWNLOADS_ROOT).trim() || DEFAULT_DOWNLOADS_ROOT;
const DOWNLOADS_FILES_DIR = path.join(DOWNLOADS_ROOT, 'files');
const DOWNLOADS_INDEX_FILE = path.join(DOWNLOADS_ROOT, 'index.json');
const ARTIFACT_ID_PATTERN = /^[A-Za-z0-9][A-Za-z0-9._-]{0,127}$/;

function normalizeString(value) {
  return typeof value === 'string' ? value.trim() : '';
}

function normalizeNumber(value) {
  const parsed = Number(value);
  if (!Number.isFinite(parsed) || parsed < 0) {
    return 0;
  }

  return parsed;
}

function normalizeArtifactId(value) {
  const artifactId = normalizeString(value);
  return ARTIFACT_ID_PATTERN.test(artifactId) ? artifactId : '';
}

export function sanitizeCodexDownloadFilename(value) {
  const filename = path.basename(normalizeString(value)) || 'artifact';
  return filename.replace(/[<>:"/\\|?*\u0000-\u001f]+/g, '-').trim() || 'artifact';
}

function normalizeArtifact(item) {
  if (!item || typeof item !== 'object') {
    return null;
  }

  const id = normalizeArtifactId(item.id);
  const filename = sanitizeCodexDownloadFilename(item.filename);
  if (!id || !filename) {
    return null;
  }

  return {
    id,
    filename,
    contentType: normalizeString(item.contentType) || 'application/octet-stream',
    size: normalizeNumber(item.size),
    sha256: normalizeString(item.sha256),
    sourceThreadId: normalizeString(item.sourceThreadId),
    sourceThreadName: normalizeString(item.sourceThreadName),
    createdAt: normalizeString(item.createdAt),
    createdBy: normalizeString(item.createdBy),
    label: normalizeString(item.label),
    notes: normalizeString(item.notes),
  };
}

function sortArtifacts(items) {
  return [...items].sort((left, right) => {
    const rightTime = Date.parse(right.createdAt || '') || 0;
    const leftTime = Date.parse(left.createdAt || '') || 0;
    return rightTime - leftTime;
  });
}

async function ensureDownloadsStorage() {
  await fs.mkdir(DOWNLOADS_FILES_DIR, { recursive: true });
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

export function getCodexDownloadsRoot() {
  return DOWNLOADS_ROOT;
}

export function getCodexDownloadsFilesDir() {
  return DOWNLOADS_FILES_DIR;
}

export function getCodexDownloadsIndexFile() {
  return DOWNLOADS_INDEX_FILE;
}

export function buildCodexDownloadStorageName(item) {
  const artifactId = normalizeArtifactId(item?.id);
  if (!artifactId) {
    return '';
  }

  return `${artifactId}-${sanitizeCodexDownloadFilename(item?.filename || 'artifact')}`;
}

export function resolveCodexDownloadFilePath(item) {
  const storageName = buildCodexDownloadStorageName(item);
  if (!storageName) {
    return '';
  }

  return path.join(DOWNLOADS_FILES_DIR, storageName);
}

export async function readCodexDownloadsManifest() {
  await ensureDownloadsStorage();
  const payload = await readJsonFile(DOWNLOADS_INDEX_FILE, []);
  if (!Array.isArray(payload)) {
    return [];
  }

  return sortArtifacts(payload.map(normalizeArtifact).filter(Boolean));
}

export async function writeCodexDownloadsManifest(items) {
  await ensureDownloadsStorage();
  const normalized = sortArtifacts(
    Array.isArray(items) ? items.map(normalizeArtifact).filter(Boolean) : [],
  );
  await writeJsonFileAtomic(DOWNLOADS_INDEX_FILE, normalized);
  return normalized;
}

export async function upsertCodexDownload(item) {
  const normalized = normalizeArtifact(item);
  if (!normalized) {
    const error = new Error('CODEX_DOWNLOAD_ARTIFACT_INVALID');
    error.code = 'CODEX_DOWNLOAD_ARTIFACT_INVALID';
    throw error;
  }

  const current = await readCodexDownloadsManifest();
  const nextItems = [
    normalized,
    ...current.filter((entry) => entry.id !== normalized.id),
  ];
  await writeCodexDownloadsManifest(nextItems);
  return normalized;
}

export async function listCodexDownloads() {
  return readCodexDownloadsManifest();
}

export async function readCodexDownload(artifactId) {
  const normalizedArtifactId = normalizeArtifactId(artifactId);
  if (!normalizedArtifactId) {
    return null;
  }

  const items = await readCodexDownloadsManifest();
  return items.find((item) => item.id === normalizedArtifactId) || null;
}

export async function readCodexDownloadFile(artifactId) {
  const item = await readCodexDownload(artifactId);
  if (!item) {
    return null;
  }

  const filePath = resolveCodexDownloadFilePath(item);
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
