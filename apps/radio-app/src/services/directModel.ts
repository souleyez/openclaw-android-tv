import type { ModelLeasePayload } from './modelLease';

type ChatResponse = {
  choices?: Array<{
    message?: {
      content?: string;
    };
  }>;
};

const DIRECT_MODEL_TIMEOUT_MS = 12000;
const DIRECT_MODEL_MAX_ATTEMPTS = 3;
const DIRECT_MODEL_RETRY_DELAY_MS = 1200;

function sleep(ms: number) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

function isRetryableStatus(status: number) {
  return [408, 409, 425, 429, 500, 502, 503, 504].includes(status);
}

function sanitizeModelReply(content?: string | null) {
  if (!content) {
    return '';
  }

  return content
    .replace(/<think>[\s\S]*?<\/think>/gi, ' ')
    .replace(/```(?:json)?/gi, ' ')
    .replace(/\s+/g, ' ')
    .trim();
}

export async function chatWithLeasedModel(params: {
  lease: ModelLeasePayload;
  text: string;
  locale: string;
}) {
  const systemPrompt = [
    'You are the AI anchor for an internet radio app.',
    'Be concise and natural.',
    'Prefer short spoken-style replies.',
    `Reply in the user locale when possible. Locale: ${params.locale}.`,
  ].join(' ');

  let lastError: Error | null = null;

  for (let attempt = 1; attempt <= DIRECT_MODEL_MAX_ATTEMPTS; attempt += 1) {
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), DIRECT_MODEL_TIMEOUT_MS);

    try {
      const response = await fetch(`${params.lease.baseUrl}/chat/completions`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${params.lease.apiKey}`,
        },
        body: JSON.stringify({
          model: params.lease.model,
          temperature: 0.6,
          messages: [
            { role: 'system', content: systemPrompt },
            { role: 'user', content: params.text },
          ],
        }),
        signal: controller.signal,
      });

      if (!response.ok) {
        if (attempt < DIRECT_MODEL_MAX_ATTEMPTS && isRetryableStatus(response.status)) {
          await sleep(DIRECT_MODEL_RETRY_DELAY_MS * attempt);
          continue;
        }

        throw new Error(`Direct model request failed: ${response.status}`);
      }

      const payload = (await response.json()) as ChatResponse;
      return sanitizeModelReply(payload.choices?.[0]?.message?.content);
    } catch (error) {
      lastError = error instanceof Error ? error : new Error('Direct model request failed');
      if (attempt < DIRECT_MODEL_MAX_ATTEMPTS) {
        await sleep(DIRECT_MODEL_RETRY_DELAY_MS * attempt);
        continue;
      }
    } finally {
      clearTimeout(timeout);
    }
  }

  throw lastError ?? new Error('Direct model request failed');
}
