import { NextResponse } from 'next/server';
import { syncRelayAgent } from '../../../../../lib/codex-relay';

export const runtime = 'nodejs';
export const dynamic = 'force-dynamic';

function resolveStatus(code) {
  if (code === 'CODEX_RELAY_UNAUTHORIZED') {
    return 401;
  }
  if (code === 'CODEX_RELAY_AGENT_MISMATCH') {
    return 403;
  }
  if (code === 'CODEX_RELAY_AGENT_ID_INVALID') {
    return 400;
  }
  if (code === 'CODEX_RELAY_DISABLED' || code === 'CODEX_RELAY_TOKEN_REQUIRED') {
    return 503;
  }

  return 500;
}

export async function POST(request) {
  try {
    const payload = await request.json().catch(() => ({}));
    const item = await syncRelayAgent(request, payload);
    return NextResponse.json({ item });
  } catch (error) {
    const code = error?.code || (error instanceof Error ? error.message : 'CODEX_RELAY_SYNC_FAILED');
    return NextResponse.json({
      error: code,
    }, { status: resolveStatus(code) });
  }
}
