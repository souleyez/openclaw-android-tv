import { NextResponse } from 'next/server';
import { readCodexDownload } from '../../../../lib/codex-downloads';
import { resolveCodexRequestAccess } from '../../../../lib/codex-access';

export const runtime = 'nodejs';
export const dynamic = 'force-dynamic';

export async function GET(request, { params }) {
  const access = await resolveCodexRequestAccess(request);
  if (!access.allowed) {
    return NextResponse.json({
      error: 'CODEX_ACCESS_REQUIRED',
    }, { status: 401 });
  }

  try {
    const artifactId = String(params?.artifactId || '').trim();
    const item = await readCodexDownload(artifactId);
    if (!item) {
      return NextResponse.json({
        error: 'CODEX_DOWNLOAD_NOT_FOUND',
      }, { status: 404 });
    }

    return NextResponse.json({ item });
  } catch (error) {
    const code = error?.code || (error instanceof Error ? error.message : 'CODEX_DOWNLOAD_LOAD_FAILED');
    return NextResponse.json({
      error: code,
    }, { status: 500 });
  }
}
