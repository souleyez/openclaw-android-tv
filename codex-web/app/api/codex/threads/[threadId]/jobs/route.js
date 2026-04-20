import { NextResponse } from 'next/server';
import { queueCodexResumeJob } from '../../../../../lib/codex-control';
import {
  resolveTrustedDeviceRequestAccess,
  TRUSTED_DEVICE_REQUIRED_ERROR,
} from '../../../../../lib/codex-access';

export const runtime = 'nodejs';
export const dynamic = 'force-dynamic';

export async function POST(request, { params }) {
  const access = await resolveTrustedDeviceRequestAccess(request);
  if (!access.allowed) {
    return NextResponse.json({
      error: TRUSTED_DEVICE_REQUIRED_ERROR,
    }, { status: 401 });
  }

  try {
    const body = await request.json();
    const prompt = typeof body?.prompt === 'string' ? body.prompt : '';
    const model = typeof body?.model === 'string' ? body.model.trim() : '';
    const reasoningEffort = typeof body?.reasoningEffort === 'string' ? body.reasoningEffort.trim() : '';
    const threadName = typeof body?.threadName === 'string' ? body.threadName.trim() : '';
    const attachments = Array.isArray(body?.attachments)
      ? body.attachments
        .map((item) => {
          if (!item || typeof item !== 'object') {
            return null;
          }

          return {
            id: typeof item.id === 'string' ? item.id.trim() : '',
          };
        })
        .filter((item) => item?.id)
      : [];
    const item = await queueCodexResumeJob({
      sessionId: String(params?.threadId || '').trim(),
      prompt,
      model,
      reasoningEffort,
      threadName,
      attachments,
    });

    return NextResponse.json({ item }, { status: 202 });
  } catch (error) {
    const code = error?.code || (error instanceof Error ? error.message : 'CODEX_JOB_CREATE_FAILED');
    const status = code === 'CODEX_JOB_ALREADY_RUNNING'
      || code === 'CODEX_DESKTOP_BUSY'
      ? 409
      : code === 'CODEX_RELAY_OFFLINE'
        ? 503
      : code === 'CODEX_THREAD_NOT_FOUND'
        || code === 'CODEX_MODEL_INVALID'
        || code === 'CODEX_PROMPT_REQUIRED'
        || code === 'CODEX_UPLOAD_NOT_FOUND'
        || code === 'CODEX_ATTACHMENTS_TOO_MANY'
        ? 400
        : 500;

    return NextResponse.json({
      error: code,
      activeJob: error?.activeJob || null,
    }, { status });
  }
}
