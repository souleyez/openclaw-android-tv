import { Injectable, Logger } from '@nestjs/common';

type QueueTask = {
  key: string;
  run: () => Promise<void>;
};

@Injectable()
export class RadioTaskQueueService {
  private readonly logger = new Logger(RadioTaskQueueService.name);
  private readonly queuedKeys = new Set<string>();
  private readonly runningKeys = new Set<string>();
  private readonly queue: QueueTask[] = [];
  private activeWorkers = 0;
  private readonly concurrency = this.resolveConcurrency();

  enqueue(key: string, run: () => Promise<void>) {
    if (this.queuedKeys.has(key) || this.runningKeys.has(key)) {
      return false;
    }

    this.queuedKeys.add(key);
    this.queue.push({ key, run });
    void this.drain();
    return true;
  }

  getSnapshot() {
    return {
      concurrency: this.concurrency,
      queued: this.queue.length,
      running: this.activeWorkers,
    };
  }

  private async drain() {
    while (this.activeWorkers < this.concurrency && this.queue.length > 0) {
      const next = this.queue.shift();
      if (next == null) {
        return;
      }

      this.queuedKeys.delete(next.key);
      this.runningKeys.add(next.key);
      this.activeWorkers += 1;

      void next
        .run()
        .catch((error: unknown) => {
          this.logger.warn(
            error instanceof Error
              ? `Background task failed for ${next.key}: ${error.message}`
              : `Background task failed for ${next.key}`,
          );
        })
        .finally(() => {
          this.runningKeys.delete(next.key);
          this.activeWorkers = Math.max(0, this.activeWorkers - 1);
          void this.drain();
        });
    }
  }

  private resolveConcurrency() {
    const raw = Number.parseInt(process.env.RADIO_TASK_QUEUE_CONCURRENCY ?? '1', 10);
    if (Number.isNaN(raw)) {
      return 1;
    }

    return Math.max(1, Math.min(2, raw));
  }
}
