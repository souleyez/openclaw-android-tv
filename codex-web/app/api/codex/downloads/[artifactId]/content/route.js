import { createReadStream } from 'node:fs';
import { Readable } from 'node:stream';
import { NextResponse } from 'next/server';
import {
  readCodexDownloadFile,
  sanitizeCodexDownloadFilename,
} from '../../../../../lib/codex-downloads';
import { resolveCodexRequestAccess } from '../../../../../lib/codex-access';

export const runtime = 'nodejs';
export const dynamic = 'force-dynamic';

function buildContentDisposition(filename) {
  const safeFilename = sanitizeCodexDownloadFilename(filename);
  const asciiFallback = safeFilename.replace(/[^\x20-\x7E]+/g, '_').replace(/["\\]/g, '') || 'artifact';
  const encodedFilename = encodeURIComponent(safeFilename);
  return `attachment; filename="${asciiFallback}"; filename*=UTF-8''${encodedFilename}`;
}

export async function GET(request, { params }) {
  const access = await resolveCodexRequestAccess(request);
  if (!access.allowed) {
    return NextResponse.json({
      error: 'CODEX_ACCESS_REQUIRED',
    }, { status: 401 });
  }

  try {
    const artifactId = String(params?.artifactId || '').trim();
    const file = await readCodexDownloadFile(artifactId);
    if (!file) {
      return NextResponse.json({
        error: 'CODEX_DOWNLOAD_NOT_FOUND',
      }, { status: 404 });
    }

    const stream = Readable.toWeb(createReadStream(file.filePath));
    return new NextResponse(stream, {
      headers: {
        'Content-Type': file.item.contentType || 'application/octet-stream',
        'Content-Length': String(file.stat.size),
        'Content-Disposition': buildContentDisposition(file.item.filename),
        'Cache-Control': 'private, no-store',
        'X-Content-Type-Options': 'nosniff',
      },
    });
  } catch (error) {
    const code = error?.code || (error instanceof Error ? error.message : 'CODEX_DOWNLOAD_STREAM_FAILED');
    return NextResponse.json({
      error: code,
    }, { status: 500 });
  }
}
