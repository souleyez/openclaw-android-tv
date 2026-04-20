import { NextResponse } from 'next/server';
import { getActiveCodexJob, readCodexJob } from '../../../../lib/codex-control';
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
    const jobId = String(params?.jobId || '').trim();
    const item = await readCodexJob(jobId);
    if (!item) {
      return NextResponse.json({
        error: 'CODEX_JOB_NOT_FOUND',
      }, { status: 404 });
    }

    const activeJob = await getActiveCodexJob();
    return NextResponse.json({
      item,
      activeJob,
    });
  } catch (error) {
    const code = error?.code || (error instanceof Error ? error.message : 'CODEX_JOB_LOAD_FAILED');
    const status = code === 'CODEX_BRIDGE_UNREACHABLE' || code === 'CODEX_BRIDGE_TIMEOUT' || code === 'CODEX_RELAY_OFFLINE'
      ? 503
      : 500;
    return NextResponse.json({
      error: code,
    }, { status });
  }
}
