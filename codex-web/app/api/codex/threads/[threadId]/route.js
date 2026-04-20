import { NextResponse } from 'next/server';
import {
  archiveCodexThread,
  readCodexThread,
} from '../../../../lib/codex-control';
import {
  resolveCodexRequestAccess,
  resolveTrustedDeviceRequestAccess,
  TRUSTED_DEVICE_REQUIRED_ERROR,
} from '../../../../lib/codex-access';

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
    const threadId = String(params?.threadId || '').trim();
    const threadName = new URL(request.url).searchParams.get('threadName') || '';
    const item = await readCodexThread(threadId, threadName);
    if (!item) {
      return NextResponse.json({
        error: 'CODEX_THREAD_NOT_FOUND',
      }, { status: 404 });
    }

    return NextResponse.json({ item });
  } catch (error) {
    const code = error?.code || (error instanceof Error ? error.message : 'CODEX_THREAD_LOAD_FAILED');
    const status = code === 'CODEX_BRIDGE_UNREACHABLE' || code === 'CODEX_BRIDGE_TIMEOUT' || code === 'CODEX_RELAY_OFFLINE'
      ? 503
      : 500;
    return NextResponse.json({
      error: code,
    }, { status });
  }
}

export async function DELETE(request, { params }) {
  const access = await resolveTrustedDeviceRequestAccess(request);
  if (!access.allowed) {
    return NextResponse.json({
      error: TRUSTED_DEVICE_REQUIRED_ERROR,
    }, { status: 401 });
  }

  try {
    const threadId = String(params?.threadId || '').trim();
    const item = await archiveCodexThread(threadId);
    return NextResponse.json({ item });
  } catch (error) {
    const code = error?.code || (error instanceof Error ? error.message : 'CODEX_THREAD_ARCHIVE_FAILED');
    const status = code === 'CODEX_THREAD_NOT_FOUND'
      ? 404
      : code === 'CODEX_THREAD_ARCHIVE_REQUIRES_HISTORY'
        ? 409
      : code === 'CODEX_RELAY_OFFLINE'
        || code === 'CODEX_BRIDGE_UNREACHABLE'
        || code === 'CODEX_BRIDGE_TIMEOUT'
        || code === 'CODEX_CLI_NOT_FOUND'
        ? 503
        : code === 'CODEX_RELAY_CONTROL_BUSY'
          ? 409
          : 500;

    return NextResponse.json({
      error: code,
      control: error?.control || null,
    }, { status });
  }
}
