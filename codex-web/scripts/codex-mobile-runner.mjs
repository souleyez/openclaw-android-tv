import { runQueuedCodexJob } from '../lib/codex-runtime-control.mjs';

const jobId = process.argv[2] || '';

if (!jobId) {
  process.exit(1);
}

runQueuedCodexJob(jobId)
  .then(() => {
    process.exit(0);
  })
  .catch(() => {
    process.exit(1);
  });
