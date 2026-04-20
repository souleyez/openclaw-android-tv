import { NextResponse } from 'next/server';
import {
  createCodexThread,
  listCodexThreads,
} from '../../../lib/codex-control';
import {
  resolveCodexRequestAccess,
  resolveTrustedDeviceRequestAccess,
  TRUSTED_DEVICE_REQUIRED_ERROR,
} from '../../../lib/codex-access';

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
    const archived = new URL(request.url).searchParams.get('archived') === '1';
    const items = await listCodexThreads({ archived });
    return NextResponse.json({ items });
  } catch (error) {
    const code = error?.code || (error instanceof Error ? error.message : 'CODEX_THREADS_LOAD_FAILED');
    const status = code === 'CODEX_BRIDGE_UNREACHABLE' || code === 'CODEX_BRIDGE_TIMEOUT' || code === 'CODEX_RELAY_OFFLINE'
      ? 503
      : 500;
    return NextResponse.json({
      error: code,
    }, { status });
  }
}

export async function POST(request) {
  const access = await resolveTrustedDeviceRequestAccess(request);
  if (!access.allowed) {
    return NextResponse.json({
      error: TRUSTED_DEVICE_REQUIRED_ERROR,
    }, { status: 401 });
  }

  try {
    const body = await request.json();
    const item = await createCodexThread({
      model: typeof body?.model === 'string' ? body.model.trim() : '',
      reasoningEffort: typeof body?.reasoningEffort === 'string' ? body.reasoningEffort.trim() : '',
      cwd: typeof body?.cwd === 'string' ? body.cwd.trim() : '',
      threadName: typeof body?.threadName === 'string' ? body.threadName.trim() : '',
    });
    return NextResponse.json({ item }, { status: 201 });
  } catch (error) {
    const code = error?.code || (error instanceof Error ? error.message : 'CODEX_THREAD_CREATE_FAILED');
    const status = code === 'CODEX_MODEL_INVALID'
      ? 400
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
