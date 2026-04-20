import { NextResponse } from 'next/server';
import { listCodexDownloads } from '../../../lib/codex-downloads';
import { resolveCodexRequestAccess } from '../../../lib/codex-access';

export const runtime = 'nodejs';
export const dynamic = 'force-dynamic';

export async function GET(request) {
  const access = await resolveCodexRequestAccess(request);
  if (!access.allowed) {
    return NextResponse.json({
      error: 'CODEX_ACCESS_REQUIRED',
    }, { status: 401 });
  }

  try {
    const items = await listCodexDownloads();
    return NextResponse.json({ items });
  } catch (error) {
    const code = error?.code || (error instanceof Error ? error.message : 'CODEX_DOWNLOADS_LOAD_FAILED');
    return NextResponse.json({
      error: code,
    }, { status: 500 });
  }
}
