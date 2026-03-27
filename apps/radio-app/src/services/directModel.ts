import type { ModelLeasePayload } from './modelLease';

type ChatResponse = {
  choices?: Array<{
    message?: {
      content?: string;
    };
  }>;
};

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
  });

  if (!response.ok) {
    throw new Error(`Direct model request failed: ${response.status}`);
  }

  const payload = (await response.json()) as ChatResponse;
  return payload.choices?.[0]?.message?.content?.trim() ?? '';
}
