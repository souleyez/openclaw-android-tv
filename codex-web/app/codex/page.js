import { cookies } from 'next/headers';
import { redirect } from 'next/navigation';
import CodexMobileClient from './CodexMobileClient';
import { resolveCodexCookieAccess } from '../lib/codex-access';
import { getCodexPageBootstrap, getTrustedDeviceSummary } from '../lib/codex-control';

export const runtime = 'nodejs';
export const dynamic = 'force-dynamic';
export const revalidate = 0;

export default async function CodexPage() {
  const cookieStore = await cookies();
  const access = await resolveCodexCookieAccess(cookieStore);
  if (!access.allowed) {
    redirect('/login?next=/codex');
  }

  const [bootstrap, boundDevice] = await Promise.all([
    getCodexPageBootstrap(24),
    getTrustedDeviceSummary(),
  ]);

  return (
    <CodexMobileClient
      initialAccess={access}
      initialActiveJob={bootstrap.activeJob}
      initialBoundDevice={boundDevice}
      initialBridgeStatus={bootstrap.bridgeStatus}
      initialDefaults={bootstrap.defaults}
      initialModels={bootstrap.models}
      initialSelectedThread={bootstrap.selectedThread}
      initialSelectedThreadId={bootstrap.selectedThreadId}
      initialThreads={bootstrap.threads}
    />
  );
}
