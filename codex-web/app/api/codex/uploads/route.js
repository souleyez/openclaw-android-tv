import { NextResponse } from 'next/server';
import { storeCodexUpload } from '../../../lib/codex-uploads';
import {
  resolveTrustedDeviceRequestAccess,
  TRUSTED_DEVICE_REQUIRED_ERROR,
} from '../../../lib/codex-access';

export const runtime = 'nodejs';
export const dynamic = 'force-dynamic';

function resolveStatus(code) {
  if (code === 'CODEX_UPLOAD_REQUIRED' || code === 'CODEX_UPLOAD_FILE_TYPE_INVALID') {
    return 400;
  }
  if (code === 'CODEX_UPLOAD_FILE_TOO_LARGE') {
    return 413;
  }

  return 500;
}

export async function POST(request) {
  const access = await resolveTrustedDeviceRequestAccess(request);
  if (!access.allowed) {
    return NextResponse.json({
      error: TRUSTED_DEVICE_REQUIRED_ERROR,
    }, { status: 401 });
  }

  try {
    const formData = await request.formData();
    const file = formData.get('file');
    if (!file || typeof file.arrayBuffer !== 'function') {
      return NextResponse.json({
        error: 'CODEX_UPLOAD_REQUIRED',
      }, { status: 400 });
    }

    const item = await storeCodexUpload(file, {
      createdBy: access.device?.label || 'trusted-browser',
    });
    return NextResponse.json({ item }, { status: 201 });
  } catch (error) {
    const code = error?.code || (error instanceof Error ? error.message : 'CODEX_UPLOAD_FAILED');
    return NextResponse.json({
      error: code,
    }, { status: resolveStatus(code) });
  }
}
