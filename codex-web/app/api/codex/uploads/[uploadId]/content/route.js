import { timingSafeEqual } from 'node:crypto';
import { createReadStream } from 'node:fs';
import { Readable } from 'node:stream';
import { NextResponse } from 'next/server';
import {
  readCodexUploadFile,
  sanitizeCodexUploadFilename,
} from '../../../../../lib/codex-uploads';
import { resolveCodexRequestAccess } from '../../../../../lib/codex-access';

export const runtime = 'nodejs';
export const dynamic = 'force-dynamic';

function hasRelayAccess(request) {
  const configuredToken = String(process.env.CODEX_RELAY_TOKEN || '').trim();
  const requestToken = String(request.headers.get('x-codex-relay-token') || '').trim();
  if (!configuredToken || !requestToken) {
    return false;
  }

  const configuredBuffer = Buffer.from(configuredToken, 'utf8');
  const requestBuffer = Buffer.from(requestToken, 'utf8');
  if (configuredBuffer.length !== requestBuffer.length) {
    return false;
  }

  return timingSafeEqual(configuredBuffer, requestBuffer);
}

function buildContentDisposition(filename) {
  const safeFilename = sanitizeCodexUploadFilename(filename);
  const asciiFallback = safeFilename.replace(/[^\x20-\x7E]+/g, '_').replace(/["\\]/g, '') || 'upload';
  const encodedFilename = encodeURIComponent(safeFilename);
  return `attachment; filename="${asciiFallback}"; filename*=UTF-8''${encodedFilename}`;
}

export async function GET(request, { params }) {
  if (!hasRelayAccess(request)) {
    const access = await resolveCodexRequestAccess(request);
    if (!access.allowed) {
      return NextResponse.json({
        error: 'CODEX_ACCESS_REQUIRED',
      }, { status: 401 });
    }
  }

  try {
    const uploadId = String(params?.uploadId || '').trim();
    const file = await readCodexUploadFile(uploadId);
    if (!file) {
      return NextResponse.json({
        error: 'CODEX_UPLOAD_NOT_FOUND',
      }, { status: 404 });
    }

    return new NextResponse(Readable.toWeb(createReadStream(file.filePath)), {
      headers: {
        'Content-Type': file.item.contentType || 'application/octet-stream',
        'Content-Length': String(file.stat.size),
        'Content-Disposition': buildContentDisposition(file.item.filename),
        'Cache-Control': 'private, no-store',
        'X-Content-Type-Options': 'nosniff',
      },
    });
  } catch (error) {
    const code = error?.code || (error instanceof Error ? error.message : 'CODEX_UPLOAD_STREAM_FAILED');
    return NextResponse.json({
      error: code,
    }, { status: 500 });
  }
}
