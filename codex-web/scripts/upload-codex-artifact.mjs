#!/usr/bin/env node

import { spawn } from 'node:child_process';
import { createHash, randomUUID } from 'node:crypto';
import fs from 'node:fs';
import fsp from 'node:fs/promises';
import path from 'node:path';

const DEFAULT_SSH_TARGET = String(process.env.CODEX_DOWNLOADS_SSH_TARGET || '1服务器').trim() || '1服务器';
const DEFAULT_REMOTE_ROOT = String(process.env.CODEX_DOWNLOADS_REMOTE_ROOT || '/srv/home/.storage/codex-downloads').trim() || '/srv/home/.storage/codex-downloads';

const REMOTE_MANIFEST_SCRIPT = `
import fs from 'node:fs/promises';
import path from 'node:path';

const root = String(process.env.CODEX_DOWNLOADS_REMOTE_ROOT || '').trim();
const raw = String(process.env.CODEX_DOWNLOADS_ARTIFACT_JSON_B64 || '').trim();

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
  return /^[A-Za-z0-9][A-Za-z0-9._-]{0,127}$/.test(artifactId) ? artifactId : '';
}

function sanitizeFilename(value) {
  const filename = path.basename(normalizeString(value)) || 'artifact';
  return filename.replace(/[<>:"/\\\\|?*\\u0000-\\u001f]+/g, '-').trim() || 'artifact';
}

function normalizeArtifact(item) {
  if (!item || typeof item !== 'object') {
    return null;
  }

  const id = normalizeArtifactId(item.id);
  const filename = sanitizeFilename(item.filename);
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

if (!root || !raw) {
  throw new Error('CODEX_DOWNLOAD_REMOTE_ARGS_REQUIRED');
}

const entry = normalizeArtifact(
  JSON.parse(Buffer.from(raw, 'base64').toString('utf8')),
);
if (!entry) {
  throw new Error('CODEX_DOWNLOAD_ARTIFACT_INVALID');
}

const filesDir = path.join(root, 'files');
const indexPath = path.join(root, 'index.json');
await fs.mkdir(filesDir, { recursive: true });

let items = [];
try {
  const manifest = JSON.parse(await fs.readFile(indexPath, 'utf8'));
  if (Array.isArray(manifest)) {
    items = manifest.map(normalizeArtifact).filter(Boolean);
  }
} catch {
  items = [];
}

items = [
  entry,
  ...items.filter((item) => item.id !== entry.id),
].sort((left, right) => {
  const rightTime = Date.parse(right.createdAt || '') || 0;
  const leftTime = Date.parse(left.createdAt || '') || 0;
  return rightTime - leftTime;
});

const tempPath = \`\${indexPath}.\${process.pid}.\${Date.now()}.tmp\`;
await fs.writeFile(tempPath, JSON.stringify(items, null, 2), 'utf8');
await fs.rename(tempPath, indexPath);
`;

function printUsage() {
  console.log(`Usage:
  node scripts/upload-codex-artifact.mjs --file <path> [options]

Options:
  --label <text>               Friendly label shown in /codex downloads
  --source-thread-id <id>      Codex source thread id
  --source-thread-name <text>  Codex source thread name
  --notes <text>               Optional notes
  --created-by <text>          Operator marker, defaults to codex-local
  --target <ssh-target>        SSH target, defaults to 1服务器
  --remote-root <path>         Remote downloads root, defaults to /srv/home/.storage/codex-downloads
  --dry-run                    Compute metadata without uploading
  --help                       Show this message

Examples:
  node scripts/upload-codex-artifact.mjs --file C:\\temp\\report.md --label "Lease report"
  node scripts/upload-codex-artifact.mjs --file ./out/slides.pdf --source-thread-id abc --source-thread-name "汇报" --notes "final export"
`);
}

function fail(message, code = 1) {
  console.error(message);
  process.exit(code);
}

function sanitizeFilename(value) {
  const filename = path.basename(String(value || '').trim()) || 'artifact';
  return filename.replace(/[<>:"/\\|?*\u0000-\u001f]+/g, '-').trim() || 'artifact';
}

function shellQuotePosix(value) {
  return `'${String(value).replace(/'/g, `'\"'\"'`)}'`;
}

function buildRemoteFilesDir(root) {
  return path.posix.join(root, 'files');
}

function resolveContentType(filename) {
  const extension = path.extname(String(filename || '').trim()).toLowerCase();
  switch (extension) {
    case '.md':
      return 'text/markdown; charset=utf-8';
    case '.txt':
      return 'text/plain; charset=utf-8';
    case '.json':
      return 'application/json; charset=utf-8';
    case '.csv':
      return 'text/csv; charset=utf-8';
    case '.pdf':
      return 'application/pdf';
    case '.pptx':
      return 'application/vnd.openxmlformats-officedocument.presentationml.presentation';
    case '.docx':
      return 'application/vnd.openxmlformats-officedocument.wordprocessingml.document';
    case '.xlsx':
      return 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet';
    case '.zip':
      return 'application/zip';
    case '.png':
      return 'image/png';
    case '.jpg':
    case '.jpeg':
      return 'image/jpeg';
    case '.webp':
      return 'image/webp';
    default:
      return 'application/octet-stream';
  }
}

function parseArgs(argv) {
  const options = {
    label: '',
    sourceThreadId: '',
    sourceThreadName: '',
    notes: '',
    createdBy: 'codex-local',
    target: DEFAULT_SSH_TARGET,
    remoteRoot: DEFAULT_REMOTE_ROOT,
    dryRun: false,
    filePath: '',
  };

  for (let index = 0; index < argv.length; index += 1) {
    const arg = argv[index];
    switch (arg) {
      case '--help':
      case '-h':
        options.help = true;
        break;
      case '--dry-run':
        options.dryRun = true;
        break;
      case '--file':
        options.filePath = argv[index + 1] || '';
        index += 1;
        break;
      case '--label':
        options.label = argv[index + 1] || '';
        index += 1;
        break;
      case '--source-thread-id':
        options.sourceThreadId = argv[index + 1] || '';
        index += 1;
        break;
      case '--source-thread-name':
        options.sourceThreadName = argv[index + 1] || '';
        index += 1;
        break;
      case '--notes':
        options.notes = argv[index + 1] || '';
        index += 1;
        break;
      case '--created-by':
        options.createdBy = argv[index + 1] || '';
        index += 1;
        break;
      case '--target':
        options.target = argv[index + 1] || '';
        index += 1;
        break;
      case '--remote-root':
        options.remoteRoot = argv[index + 1] || '';
        index += 1;
        break;
      default:
        fail(`Unknown argument: ${arg}`);
    }
  }

  return options;
}

function runCommand(command, args, options = {}) {
  const { input = '' } = options;

  return new Promise((resolve, reject) => {
    const child = spawn(command, args, {
      stdio: ['pipe', 'pipe', 'pipe'],
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
    child.on('error', reject);
    child.on('close', (code) => {
      if (code === 0) {
        resolve({ stdout, stderr });
        return;
      }

      const error = new Error(stderr.trim() || stdout.trim() || `${command} exited with code ${code}`);
      error.code = code;
      reject(error);
    });

    if (input) {
      child.stdin.write(input);
    }
    child.stdin.end();
  });
}

function sha256File(filePath) {
  return new Promise((resolve, reject) => {
    const hash = createHash('sha256');
    const stream = fs.createReadStream(filePath);
    stream.on('error', reject);
    stream.on('data', (chunk) => {
      hash.update(chunk);
    });
    stream.on('end', () => {
      resolve(hash.digest('hex'));
    });
  });
}

async function main() {
  const options = parseArgs(process.argv.slice(2));
  if (options.help) {
    printUsage();
    return;
  }

  const sourcePath = path.resolve(String(options.filePath || '').trim());
  if (!sourcePath || sourcePath.endsWith(path.sep)) {
    fail('Missing required --file <path>.');
  }

  const stat = await fsp.stat(sourcePath).catch(() => null);
  if (!stat?.isFile()) {
    fail(`File not found: ${sourcePath}`);
  }

  const filename = sanitizeFilename(path.basename(sourcePath));
  const artifactId = randomUUID();
  const storageName = `${artifactId}-${filename}`;
  const remoteRoot = String(options.remoteRoot || '').trim() || DEFAULT_REMOTE_ROOT;
  const remoteFilesDir = buildRemoteFilesDir(remoteRoot);
  const remoteFilePath = path.posix.join(remoteFilesDir, storageName);
  const sha256 = await sha256File(sourcePath);

  const artifact = {
    id: artifactId,
    filename,
    contentType: resolveContentType(filename),
    size: stat.size,
    sha256,
    sourceThreadId: String(options.sourceThreadId || '').trim(),
    sourceThreadName: String(options.sourceThreadName || '').trim(),
    createdAt: new Date().toISOString(),
    createdBy: String(options.createdBy || '').trim() || 'codex-local',
    label: String(options.label || '').trim() || filename,
    notes: String(options.notes || '').trim(),
  };

  if (options.dryRun) {
    console.log(JSON.stringify({
      dryRun: true,
      target: options.target,
      remoteRoot,
      remoteFilePath,
      artifact,
    }, null, 2));
    return;
  }

  const mkdirCommand = `mkdir -p ${shellQuotePosix(remoteFilesDir)}`;
  await runCommand('ssh', [options.target, mkdirCommand]);
  await runCommand('scp', [sourcePath, `${options.target}:${remoteFilePath}`]);

  const artifactJsonB64 = Buffer.from(JSON.stringify(artifact), 'utf8').toString('base64');
  const remoteCommand = [
    `CODEX_DOWNLOADS_REMOTE_ROOT=${shellQuotePosix(remoteRoot)}`,
    `CODEX_DOWNLOADS_ARTIFACT_JSON_B64=${shellQuotePosix(artifactJsonB64)}`,
    'node --input-type=module -',
  ].join(' ');
  await runCommand('ssh', [options.target, remoteCommand], {
    input: REMOTE_MANIFEST_SCRIPT,
  });

  console.log(JSON.stringify({
    uploaded: true,
    target: options.target,
    remoteRoot,
    remoteFilePath,
    artifact,
  }, null, 2));
}

main().catch((error) => {
  fail(error instanceof Error ? error.message : String(error));
});
