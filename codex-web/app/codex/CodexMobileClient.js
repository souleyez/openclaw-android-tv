'use client';

import { startTransition, useEffect, useRef, useState } from 'react';

function formatDateTime(value) {
  if (!value) {
    return '';
  }

  const timestamp = Date.parse(value);
  if (Number.isNaN(timestamp)) {
    return value;
  }

  return new Intl.DateTimeFormat('zh-CN', {
    dateStyle: 'medium',
    timeStyle: 'short',
  }).format(new Date(timestamp));
}

function formatBytes(value) {
  const size = Number(value);
  if (!Number.isFinite(size) || size <= 0) {
    return '';
  }

  if (size < 1024) {
    return `${size} B`;
  }
  if (size < 1024 * 1024) {
    return `${(size / 1024).toFixed(1)} KB`;
  }

  return `${(size / (1024 * 1024)).toFixed(1)} MB`;
}

function requestJson(url, init = {}) {
  const hasFormDataBody = typeof FormData !== 'undefined' && init.body instanceof FormData;
  return fetch(url, {
    ...init,
    headers: {
      ...((init.body !== undefined && !hasFormDataBody) ? { 'Content-Type': 'application/json' } : {}),
      ...(init.headers || {}),
    },
  }).then(async (response) => {
    const payload = await response.json().catch(() => ({}));
    if (!response.ok) {
      const error = new Error(payload.error || payload.code || response.statusText);
      error.activeJob = payload.activeJob || null;
      throw error;
    }
    return payload;
  });
}

function isJobRunning(job) {
  return job && (job.status === 'queued' || job.status === 'running');
}

function isThreadBusy(threadId, selectedThread, activeJob) {
  if (!threadId) {
    return false;
  }

  if (activeJob?.sessionId === threadId && isJobRunning(activeJob)) {
    return true;
  }

  return Boolean(selectedThread?.id === threadId && selectedThread?.running);
}

function summarizeModel(models, model) {
  return models.find((item) => item.slug === model) || null;
}

function resolveReasoningOptions(models, model, fallbackEffort) {
  const selectedModel = summarizeModel(models, model);
  const supported = selectedModel?.supportedReasoningLevels || [];
  if (supported.length) {
    return supported;
  }

  return fallbackEffort
    ? [{ effort: fallbackEffort, description: '' }]
    : [];
}

function buildOptimisticMessage(text) {
  const timestamp = new Date().toISOString();
  return {
    id: `optimistic-${timestamp}`,
    role: 'user',
    text,
    truncated: false,
    timestamp,
    phase: 'input',
    localOnly: true,
  };
}

function buildSyntheticAssistantMessage(text, timestamp = new Date().toISOString()) {
  return {
    id: `job-final-${timestamp}`,
    role: 'assistant',
    text,
    truncated: false,
    timestamp,
    phase: 'final',
    localOnly: true,
  };
}

function sameConversationMessage(left, right) {
  return left?.role === right?.role && left?.text === right?.text;
}

function normalizeMessages(messages) {
  return messages.filter((item) => item?.role && item?.text).slice(-60);
}

function mergeThreadMessages(baseMessages, localMessages) {
  const next = [...normalizeMessages(baseMessages)];
  for (const item of normalizeMessages(localMessages)) {
    if (next.some((current) => sameConversationMessage(current, item))) {
      continue;
    }

    next.push(item);
  }

  return next.slice(-60);
}

function buildFallbackMessages(thread) {
  const fallback = [];
  if (thread?.latestUserMessage) {
    fallback.push({
      id: `fallback-user-${thread.id || 'thread'}`,
      role: 'user',
      text: thread.latestUserMessage,
      truncated: Boolean(thread.latestUserMessageTruncated),
      timestamp: thread.updatedAt || '',
      phase: '',
    });
  }

  if (thread?.latestReply) {
    fallback.push({
      id: `fallback-assistant-${thread.id || 'thread'}`,
      role: 'assistant',
      text: thread.latestReply,
      truncated: Boolean(thread.latestReplyTruncated),
      timestamp: thread.updatedAt || '',
      phase: 'final',
    });
  }

  return fallback;
}

function mergeThreadDetail(nextThread, currentThread = null) {
  if (!nextThread) {
    return currentThread || null;
  }

  const nextMessages = Array.isArray(nextThread.messages) && nextThread.messages.length
    ? nextThread.messages
    : buildFallbackMessages(nextThread);
  const currentMessages = Array.isArray(currentThread?.messages)
    ? currentThread.messages.filter((item) => item?.localOnly)
    : [];
  const mergedMessages = mergeThreadMessages(nextMessages, currentMessages);
  const latestUserMessage = nextThread.latestUserMessage
    || [...mergedMessages].reverse().find((item) => item.role === 'user')?.text
    || '';
  const latestReply = nextThread.latestReply
    || [...mergedMessages].reverse().find((item) => item.role === 'assistant')?.text
    || '';

  return {
    ...nextThread,
    latestUserMessage,
    latestUserMessageTruncated: nextThread.latestUserMessageTruncated || false,
    latestReply,
    latestReplyTruncated: nextThread.latestReplyTruncated || false,
    messages: mergedMessages,
  };
}

function appendLocalMessage(thread, nextMessage) {
  if (!thread) {
    return thread;
  }

  const base = mergeThreadDetail(thread);
  if (base.messages.some((item) => sameConversationMessage(item, nextMessage))) {
    return base;
  }

  const messages = mergeThreadMessages(base.messages || [], [nextMessage]);

  return {
    ...base,
    updatedAt: nextMessage.timestamp || base.updatedAt,
    latestUserMessage: nextMessage.role === 'user' ? nextMessage.text : base.latestUserMessage,
    latestUserMessageTruncated: nextMessage.role === 'user' ? Boolean(nextMessage.truncated) : base.latestUserMessageTruncated,
    latestReply: nextMessage.role === 'assistant' ? nextMessage.text : base.latestReply,
    latestReplyTruncated: nextMessage.role === 'assistant' ? Boolean(nextMessage.truncated) : base.latestReplyTruncated,
    messages,
  };
}

function describeMessage(item) {
  if (item.role === 'user') {
    return '你';
  }
  if (item.phase === 'commentary') {
    return '处理中';
  }
  if (item.phase === 'final') {
    return 'Codex';
  }
  return '回复';
}

function describeErrorCode(code) {
  if (code === 'TRUSTED_DEVICE_REQUIRED') {
    return '当前浏览器不可写';
  }
  if (code === 'TRUSTED_DEVICE_REBIND_REQUIRED') {
    return '已有其他受信终端';
  }
  if (code === 'CODEX_THREAD_REQUIRED') {
    return '请选择线程';
  }
  if (code === 'CODEX_PROMPT_REQUIRED') {
    return '请输入内容';
  }
  if (code === 'PLATFORM_KEY_REQUIRED') {
    return '请输入平台密钥';
  }
  if (code === 'CODEX_ACCESS_REQUIRED') {
    return '当前会话已失效';
  }
  if (code === 'CODEX_THREAD_NOT_FOUND') {
    return '线程不存在';
  }
  if (code === 'CODEX_THREAD_ARCHIVE_REQUIRES_HISTORY') {
    return '空线程暂不能归档';
  }
  if (code === 'CODEX_UPLOAD_REQUIRED') {
    return '请选择文件';
  }
  if (code === 'CODEX_UPLOAD_NOT_FOUND') {
    return '附件不存在';
  }
  if (code === 'CODEX_UPLOAD_FILE_TYPE_INVALID') {
    return '文件类型不支持';
  }
  if (code === 'CODEX_UPLOAD_FILE_TOO_LARGE') {
    return '文件太大';
  }
  if (code === 'CODEX_ATTACHMENTS_TOO_MANY') {
    return '附件过多';
  }
  if (code === 'CODEX_DOWNLOAD_NOT_FOUND') {
    return '产物不存在';
  }
  if (code === 'CODEX_MODEL_INVALID') {
    return '模型不可用';
  }
  if (
    code === 'CODEX_RELAY_OFFLINE'
    || code === 'CODEX_BRIDGE_UNREACHABLE'
    || code === 'CODEX_BRIDGE_TIMEOUT'
  ) {
    return 'Codex 不可用';
  }
  if (code === 'CODEX_RELAY_CONTROL_BUSY') {
    return 'Codex 正忙';
  }
  return code;
}

function ThreadListItem({
  active,
  item,
  onSelect,
  running,
}) {
  return (
    <button
      type="button"
      className={`cp-codex-mobile-thread-item ${active ? 'active' : ''} ${running ? 'running' : ''}`}
      onClick={() => onSelect(item.id, item.threadName)}
    >
      <strong>{item.threadName}</strong>
      {running ? <span className="cp-codex-mobile-thread-dot" /> : null}
    </button>
  );
}

function ConversationMessage({ item }) {
  return (
    <article className={`cp-codex-mobile-message-row ${item.role === 'user' ? 'user' : 'assistant'}`}>
      <div className="cp-codex-mobile-message-meta">
        <span>{describeMessage(item)}</span>
        <time dateTime={item.timestamp || undefined}>{formatDateTime(item.timestamp)}</time>
      </div>
      <div className={`cp-codex-mobile-message-bubble ${item.role === 'user' ? 'user' : 'assistant'}`}>
        {item.text}
      </div>
    </article>
  );
}

function AttachmentChip({ item, onRemove }) {
  return (
    <div className="cp-codex-mobile-attachment-chip">
      <span title={item.filename}>
        {item.mediaType === 'image' ? '图片' : '文件'}
        {' '}
        {item.filename}
      </span>
      <button
        className="cp-ghost-btn cp-codex-mobile-attachment-remove"
        type="button"
        onClick={() => onRemove(item.id)}
      >
        移除
      </button>
    </div>
  );
}

function ArtifactListItem({ item }) {
  const downloadHref = `/api/codex/downloads/${encodeURIComponent(item.id)}/content`;
  const timestamp = formatDateTime(item.createdAt);
  const size = formatBytes(item.size);

  return (
    <article className="cp-codex-mobile-artifact-item">
      <div className="cp-codex-mobile-artifact-copy">
        <strong title={item.label || item.filename}>{item.label || item.filename}</strong>
        {item.label && item.label !== item.filename ? (
          <span title={item.filename}>{item.filename}</span>
        ) : null}
        <p>
          {[item.sourceThreadName, timestamp, size].filter(Boolean).join(' · ') || '可下载产物'}
        </p>
      </div>
      <a className="cp-ghost-btn cp-codex-mobile-artifact-download" href={downloadHref}>
        下载
      </a>
    </article>
  );
}

function InlineStatus({ kind, children }) {
  if (!children) {
    return null;
  }

  return (
    <div className={`cp-codex-mobile-inline-status ${kind}`}>
      {children}
    </div>
  );
}

export default function CodexMobileClient({
  initialAccess,
  initialActiveJob,
  initialBoundDevice,
  initialBridgeStatus,
  initialDefaults,
  initialModels,
  initialSelectedThread,
  initialSelectedThreadId,
  initialThreads,
}) {
  const shellRef = useRef(null);
  const topbarRef = useRef(null);
  const composerRef = useRef(null);
  const messageStreamRef = useRef(null);
  const fileInputRef = useRef(null);
  const selectedThreadIdRef = useRef(initialSelectedThreadId || '');
  const [access, setAccess] = useState(initialAccess);
  const [boundDevice, setBoundDevice] = useState(initialBoundDevice || null);
  const [bridgeStatus, setBridgeStatus] = useState(initialBridgeStatus || {
    mode: 'local',
    reachable: true,
    error: '',
  });
  const [threads, setThreads] = useState(initialThreads || []);
  const [selectedThreadId, setSelectedThreadId] = useState(initialSelectedThreadId || '');
  const [selectedThread, setSelectedThread] = useState(
    initialSelectedThread ? mergeThreadDetail(initialSelectedThread) : null,
  );
  const [defaults] = useState(initialDefaults);
  const [models] = useState(initialModels || []);
  const [activeJob, setActiveJob] = useState(initialActiveJob || null);
  const [prompt, setPrompt] = useState('');
  const [model, setModel] = useState(
    initialSelectedThread?.model || initialDefaults.model || initialModels?.[0]?.slug || '',
  );
  const [reasoningEffort, setReasoningEffort] = useState(
    initialSelectedThread?.reasoningEffort || initialDefaults.reasoningEffort || 'medium',
  );
  const [notice, setNotice] = useState('');
  const [error, setError] = useState('');
  const [loadingThread, setLoadingThread] = useState(false);
  const [unbinding, setUnbinding] = useState(false);
  const [sending, setSending] = useState(false);
  const [creatingThread, setCreatingThread] = useState(false);
  const [archivingThread, setArchivingThread] = useState(false);
  const [refreshingThreads, setRefreshingThreads] = useState(false);
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [keySheetOpen, setKeySheetOpen] = useState(false);
  const [artifactSheetOpen, setArtifactSheetOpen] = useState(false);
  const [controlsOpen, setControlsOpen] = useState(false);
  const [composerFocused, setComposerFocused] = useState(false);
  const [artifacts, setArtifacts] = useState([]);
  const [artifactsLoaded, setArtifactsLoaded] = useState(false);
  const [loadingArtifacts, setLoadingArtifacts] = useState(false);
  const [attachments, setAttachments] = useState([]);
  const [uploadingAttachments, setUploadingAttachments] = useState(false);
  const [platformKey, setPlatformKey] = useState('');
  const [locking, setLocking] = useState(false);
  const [keyNotice, setKeyNotice] = useState('');
  const [keyError, setKeyError] = useState('');

  useEffect(() => {
    if (!selectedThread) {
      return;
    }

    setModel(selectedThread.model || defaults.model || '');
    setReasoningEffort(selectedThread.reasoningEffort || defaults.reasoningEffort || 'medium');
  }, [defaults.model, defaults.reasoningEffort, selectedThread]);

  useEffect(() => {
    const supported = resolveReasoningOptions(models, model, defaults.reasoningEffort);
    if (!supported.length) {
      return;
    }

    const stillSupported = supported.some((item) => item.effort === reasoningEffort);
    if (!stillSupported) {
      setReasoningEffort(
        supported[0]?.effort || defaults.reasoningEffort || 'medium',
      );
    }
  }, [defaults.reasoningEffort, model, models, reasoningEffort]);

  useEffect(() => {
    const root = document.documentElement;
    const body = document.body;
    root.classList.add('cp-codex-mobile-root');
    body.classList.add('cp-codex-mobile-root-body');

    return () => {
      root.classList.remove('cp-codex-mobile-root');
      body.classList.remove('cp-codex-mobile-root-body');
    };
  }, []);

  useEffect(() => {
    const stream = messageStreamRef.current;
    if (!stream) {
      return;
    }

    stream.scrollTo({
      top: stream.scrollHeight,
      behavior: composerFocused ? 'auto' : 'smooth',
    });
  }, [activeJob?.id, activeJob?.status, composerFocused, selectedThread?.id, selectedThread?.messages?.length]);

  useEffect(() => {
    if (!shellRef.current) {
      return undefined;
    }

    const shell = shellRef.current;
    const viewport = window.visualViewport;
    let frameId = 0;

    const applyViewportMetrics = () => {
      frameId = 0;
      const height = viewport?.height || window.innerHeight;
      const offsetTop = viewport?.offsetTop || 0;
      shell.style.setProperty('--cp-codex-vv-height', `${Math.max(height, 0)}px`);
      shell.style.setProperty('--cp-codex-vv-offset-top', `${Math.max(offsetTop, 0)}px`);
    };

    const scheduleViewportMetrics = () => {
      if (frameId) {
        return;
      }
      frameId = window.requestAnimationFrame(applyViewportMetrics);
    };

    applyViewportMetrics();
    window.addEventListener('resize', scheduleViewportMetrics);
    viewport?.addEventListener('resize', scheduleViewportMetrics);
    viewport?.addEventListener('scroll', scheduleViewportMetrics);

    return () => {
      if (frameId) {
        window.cancelAnimationFrame(frameId);
      }
      window.removeEventListener('resize', scheduleViewportMetrics);
      viewport?.removeEventListener('resize', scheduleViewportMetrics);
      viewport?.removeEventListener('scroll', scheduleViewportMetrics);
    };
  }, []);

  useEffect(() => {
    const busyThread = Boolean(selectedThread?.running);
    if (!selectedThreadId || (!isJobRunning(activeJob) && !busyThread)) {
      return undefined;
    }

    let polling = false;
    const timer = setInterval(async () => {
      if (polling) {
        return;
      }

      polling = true;
      const activeThreadId = selectedThreadId || activeJob?.sessionId || '';
      try {
        let payload = null;
        if (activeJob?.id) {
          payload = await requestJson(`/api/codex/jobs/${encodeURIComponent(activeJob.id)}`);
          const job = payload.activeJob || payload.item || null;
          startTransition(() => {
            setActiveJob(job);
          });
        }

        if (activeThreadId) {
          await refreshSelectedThreadState(
            activeThreadId,
            resolveThreadName(activeThreadId, selectedThread?.threadName || ''),
          );
        }

        if (!payload?.activeJob) {
          if (payload?.item?.status === 'succeeded' && payload.item.finalMessage) {
            startTransition(() => {
              setSelectedThread((current) => appendLocalMessage(
                current,
                buildSyntheticAssistantMessage(
                  payload.item.finalMessage,
                  payload.item.finishedAt || new Date().toISOString(),
                ),
              ));
            });
          }

          await syncThreadsAndSelection();
          if (payload?.item?.status === 'succeeded') {
            setNotice('线程已完成，可继续发送');
            setError('');
          } else if (payload?.item?.status === 'failed') {
            setNotice('线程已中断，可继续发送');
            setError(payload.item.error || 'CODEX_JOB_FAILED');
          }
        }
      } catch (nextError) {
        setError(nextError instanceof Error ? nextError.message : String(nextError));
      } finally {
        polling = false;
      }
    }, 2500);

    return () => clearInterval(timer);
  }, [activeJob?.id, activeJob?.sessionId, activeJob?.status, selectedThread?.running, selectedThread?.threadName, selectedThreadId, threads]);

  useEffect(() => {
    if (isJobRunning(activeJob) || selectedThread?.running) {
      return undefined;
    }

    let polling = false;
    const timer = setInterval(async () => {
      if (polling) {
        return;
      }

      polling = true;
      try {
        await syncThreadsAndSelection();
        setError('');
      } catch (nextError) {
        setError(nextError instanceof Error ? nextError.message : String(nextError));
      } finally {
        polling = false;
      }
    }, 6000);

    return () => clearInterval(timer);
  }, [activeJob?.id, activeJob?.status, selectedThread?.running, selectedThreadId]);

  useEffect(() => {
    selectedThreadIdRef.current = selectedThreadId;
  }, [selectedThreadId]);

  useEffect(() => {
    setAttachments([]);
  }, [selectedThreadId]);

  function resetKeySheetMessages() {
    setKeyNotice('');
    setKeyError('');
  }

  function openKeySheet() {
    resetKeySheetMessages();
    setKeySheetOpen(true);
    setDrawerOpen(false);
    setArtifactSheetOpen(false);
  }

  function closePanels() {
    setDrawerOpen(false);
    setKeySheetOpen(false);
    setArtifactSheetOpen(false);
  }

  function resolveThreadName(threadId, fallbackName = '') {
    if (!threadId) {
      return fallbackName;
    }

    if (selectedThread?.id === threadId && selectedThread?.threadName) {
      return selectedThread.threadName;
    }

    return threads.find((item) => item.id === threadId)?.threadName || fallbackName;
  }

  async function refreshSelectedThreadState(threadId, threadName = '') {
    if (!threadId) {
      startTransition(() => {
        setSelectedThreadId('');
        setSelectedThread(null);
      });
      return null;
    }

    const payload = await requestJson(
      `/api/codex/threads/${encodeURIComponent(threadId)}?threadName=${encodeURIComponent(resolveThreadName(threadId, threadName))}`,
    );

    startTransition(() => {
      setSelectedThreadId(threadId);
      setSelectedThread((current) => mergeThreadDetail(payload.item || null, current));
    });

    return payload.item || null;
  }

  async function syncThreadsAndSelection() {
    const payload = await requestJson('/api/codex/threads');
    const nextItems = payload.items || [];
    startTransition(() => {
      setThreads(nextItems);
    });

    if (!nextItems.length) {
      startTransition(() => {
        setSelectedThreadId('');
        setSelectedThread(null);
      });
      return {
        items: nextItems,
        selected: null,
      };
    }

    const currentSummary = nextItems.find((item) => item.id === selectedThreadId) || null;
    const nextSummary = currentSummary || nextItems[0];

    try {
      const selected = await refreshSelectedThreadState(nextSummary.id, nextSummary.threadName);
      return {
        items: nextItems,
        selected,
      };
    } catch (nextError) {
      const code = nextError instanceof Error ? nextError.message : String(nextError);
      if (code !== 'CODEX_THREAD_NOT_FOUND') {
        throw nextError;
      }

      const fallbackSummary = nextItems.find((item) => item.id !== nextSummary.id) || null;
      if (!fallbackSummary) {
        startTransition(() => {
          setSelectedThreadId('');
          setSelectedThread(null);
        });
        return {
          items: nextItems,
          selected: null,
        };
      }

      const selected = await refreshSelectedThreadState(fallbackSummary.id, fallbackSummary.threadName);
      return {
        items: nextItems,
        selected,
      };
    }
  }

  async function refreshAccessState() {
    const payload = await requestJson('/api/codex/access');
    startTransition(() => {
      if (payload.access) {
        setAccess(payload.access);
      }
      setBoundDevice(payload.boundDevice || payload.access?.device || null);
      if (payload.bridgeStatus) {
        setBridgeStatus(payload.bridgeStatus);
      }
    });
    return payload;
  }

  useEffect(() => {
    async function pollAccessState() {
      try {
        await refreshAccessState();
      } catch {
        // Keep the last known runtime state if the status refresh request itself fails.
      }
    }

    let polling = false;
    const timer = setInterval(async () => {
      if (polling) {
        return;
      }

      polling = true;
      try {
        await pollAccessState();
      } finally {
        polling = false;
      }
    }, 10000);

    void pollAccessState();

    return () => clearInterval(timer);
  }, []);

  async function refreshThreads() {
    setRefreshingThreads(true);
    try {
      setError('');
      await syncThreadsAndSelection();
    } catch (nextError) {
      setError(nextError instanceof Error ? nextError.message : String(nextError));
    } finally {
      setRefreshingThreads(false);
    }
  }

  async function refreshArtifacts(options = {}) {
    const keepExistingError = options.keepExistingError === true;
    setLoadingArtifacts(true);
    try {
      if (!keepExistingError) {
        setError('');
      }

      const payload = await requestJson('/api/codex/downloads');
      startTransition(() => {
        setArtifacts(payload.items || []);
        setArtifactsLoaded(true);
      });
    } catch (nextError) {
      setError(nextError instanceof Error ? nextError.message : String(nextError));
    } finally {
      setLoadingArtifacts(false);
    }
  }

  async function openArtifactSheet() {
    setDrawerOpen(false);
    setKeySheetOpen(false);
    setArtifactSheetOpen(true);

    if (!artifactsLoaded && !loadingArtifacts) {
      await refreshArtifacts({ keepExistingError: true });
    }
  }

  async function handleCreateThread() {
    if (access.kind !== 'bound') {
      setError('TRUSTED_DEVICE_REQUIRED');
      return;
    }

    if (!bridgeReady) {
      setError('CODEX_RELAY_OFFLINE');
      return;
    }

    setCreatingThread(true);
    try {
      setError('');
      setNotice('');

      const payload = await requestJson('/api/codex/threads', {
        method: 'POST',
        body: JSON.stringify({
          model,
          reasoningEffort,
          cwd: '',
          threadName: '',
        }),
      });
      const nextThread = payload.item || null;

      if (nextThread?.id) {
        startTransition(() => {
          setThreads((current) => [
            nextThread,
            ...current.filter((item) => item.id !== nextThread.id),
          ]);
          setSelectedThreadId(nextThread.id);
          setSelectedThread(mergeThreadDetail(nextThread));
          setActiveJob(null);
          setDrawerOpen(false);
        });

        try {
          await refreshSelectedThreadState(nextThread.id, nextThread.threadName || '');
        } catch (nextError) {
          const code = nextError instanceof Error ? nextError.message : String(nextError);
          if (code !== 'CODEX_THREAD_NOT_FOUND') {
            throw nextError;
          }

          await syncThreadsAndSelection();
        }
      } else {
        await syncThreadsAndSelection();
      }

      setNotice('已新建线程');
    } catch (nextError) {
      setError(nextError instanceof Error ? nextError.message : String(nextError));
    } finally {
      setCreatingThread(false);
    }
  }

  async function handleArchiveThread() {
    if (access.kind !== 'bound') {
      setError('TRUSTED_DEVICE_REQUIRED');
      return;
    }

    if (!bridgeReady) {
      setError('CODEX_RELAY_OFFLINE');
      return;
    }

    if (!selectedThreadId) {
      setError('CODEX_THREAD_REQUIRED');
      return;
    }

    setArchivingThread(true);
    try {
      setError('');
      setNotice('');

      await requestJson(`/api/codex/threads/${encodeURIComponent(selectedThreadId)}`, {
        method: 'DELETE',
      });

      startTransition(() => {
        setActiveJob((current) => (
          current?.sessionId === selectedThreadId ? null : current
        ));
      });
      await syncThreadsAndSelection();
      setDrawerOpen(false);
      setNotice('已归档');
    } catch (nextError) {
      setError(nextError instanceof Error ? nextError.message : String(nextError));
    } finally {
      setArchivingThread(false);
    }
  }

  async function loadThread(threadId, threadName, shouldCloseDrawer = true) {
    if (!threadId) {
      return;
    }

    setLoadingThread(true);
    try {
      setError('');
      const payload = await requestJson(
        `/api/codex/threads/${encodeURIComponent(threadId)}?threadName=${encodeURIComponent(threadName || '')}`,
      );
      startTransition(() => {
        setSelectedThread((current) => (
          current?.id === threadId
            ? mergeThreadDetail(payload.item || null, current)
            : mergeThreadDetail(payload.item || null)
        ));
        setSelectedThreadId(threadId);
        if (shouldCloseDrawer) {
          setDrawerOpen(false);
        }
      });
    } catch (nextError) {
      setError(nextError instanceof Error ? nextError.message : String(nextError));
    } finally {
      setLoadingThread(false);
    }
  }

  async function bindCurrentBrowser() {
    const payload = await requestJson('/api/codex/access', {
      method: 'POST',
    });
    startTransition(() => {
      setAccess(payload.access || access);
      setBoundDevice(payload.access?.device || boundDevice);
    });
    return payload;
  }

  async function handleKeyLock(event) {
    event.preventDefault();
    const trimmedPlatformKey = platformKey.trim();
    if (!trimmedPlatformKey) {
      setKeyError('PLATFORM_KEY_REQUIRED');
      setKeyNotice('');
      return;
    }

    setLocking(true);
    try {
      setError('');
      setNotice('');
      resetKeySheetMessages();

      await requestJson('/api/admin/session', {
        method: 'POST',
        body: JSON.stringify({ platformKey: trimmedPlatformKey }),
      });

      const accessPayload = await refreshAccessState();
      if (accessPayload.access?.kind === 'bound') {
        setPlatformKey('');
        setNotice('已锁定');
        setKeySheetOpen(false);
        return;
      }

      if (!accessPayload.boundDevice) {
        await bindCurrentBrowser();
        setPlatformKey('');
        setNotice('已锁定');
        setKeySheetOpen(false);
        return;
      }

      setPlatformKey('');
      setKeyNotice('只读');
    } catch (nextError) {
      const message = nextError instanceof Error ? nextError.message : String(nextError);
      if (message === 'TRUSTED_DEVICE_REBIND_REQUIRED') {
        try {
          await refreshAccessState();
        } catch {
          // Ignore refresh failure and preserve the original error.
        }
      }
      setKeyError(message);
    } finally {
      setLocking(false);
    }
  }

  async function handleUnbindDevice() {
    setUnbinding(true);
    try {
      setError('');
      setNotice('');
      resetKeySheetMessages();
      await requestJson('/api/codex/access', {
        method: 'DELETE',
      });

      try {
        const payload = await refreshAccessState();
        setNotice(payload.access?.kind === 'admin' ? '已解除锁定' : '已解除锁定');
      } catch (nextError) {
        const message = nextError instanceof Error ? nextError.message : String(nextError);
        if (message === 'CODEX_ACCESS_REQUIRED') {
          window.location.href = '/login?next=/codex';
          return;
        }
        throw nextError;
      }
    } catch (nextError) {
      setError(nextError instanceof Error ? nextError.message : String(nextError));
    } finally {
      setUnbinding(false);
    }
  }

  async function handleSendPrompt(event) {
    event.preventDefault();
    const trimmedPrompt = prompt.trim();
    const effectivePrompt = trimmedPrompt || '继续';

    if (access.kind !== 'bound') {
      setError('TRUSTED_DEVICE_REQUIRED');
      return;
    }

    if (!selectedThreadId) {
      setError('CODEX_THREAD_REQUIRED');
      return;
    }

    if (isThreadBusy(selectedThreadId, selectedThread, activeJob)) {
      setError('');
      return;
    }

    setSending(true);
    try {
      setError('');
      setNotice('');
      const nextAttachments = attachments.map((item) => ({ id: item.id }));
      const payload = await requestJson(
        `/api/codex/threads/${encodeURIComponent(selectedThreadId)}/jobs`,
        {
          method: 'POST',
          body: JSON.stringify({
            prompt: effectivePrompt,
            model,
            reasoningEffort,
            threadName: selectedThread?.threadName || '',
            attachments: nextAttachments,
          }),
        },
      );

      const optimisticMessage = buildOptimisticMessage(effectivePrompt);
      startTransition(() => {
        setActiveJob(payload.item || null);
        setPrompt('');
        setAttachments([]);
        setSelectedThread((current) => appendLocalMessage(current, optimisticMessage));
      });
      setNotice('已发送');
    } catch (nextError) {
      setError(nextError instanceof Error ? nextError.message : String(nextError));
      if (nextError?.activeJob) {
        startTransition(() => {
          setActiveJob(nextError.activeJob);
        });
      }
    } finally {
      setSending(false);
    }
  }

  function handleUploadPlaceholder() {
    if (access.kind !== 'bound') {
      setError('TRUSTED_DEVICE_REQUIRED');
      return;
    }

    if (!selectedThreadId) {
      setError('CODEX_THREAD_REQUIRED');
      return;
    }

    if (uploadingAttachments) {
      return;
    }

    fileInputRef.current?.click();
  }

  function handleVoicePlaceholder() {
    setNotice('语音待接入');
    setError('');
  }

  async function handleUploadSelection(event) {
    const files = Array.from(event.target.files || []);
    event.target.value = '';

    if (!files.length) {
      return;
    }

    const targetThreadId = selectedThreadIdRef.current;
    if (!targetThreadId) {
      setError('CODEX_THREAD_REQUIRED');
      return;
    }

    setUploadingAttachments(true);
    try {
      setError('');
      setNotice('');
      const uploadedItems = [];
      let threadChanged = false;

      for (const file of files) {
        if (selectedThreadIdRef.current !== targetThreadId) {
          threadChanged = true;
          break;
        }
        const formData = new FormData();
        formData.set('file', file);
        const payload = await requestJson('/api/codex/uploads', {
          method: 'POST',
          body: formData,
        });
        if (payload?.item) {
          uploadedItems.push(payload.item);
        }
      }

      if (threadChanged) {
        setNotice(uploadedItems.length ? '线程已切换' : '');
      }

      if (uploadedItems.length && selectedThreadIdRef.current === targetThreadId) {
        startTransition(() => {
          setAttachments((current) => {
            const next = [...current];
            for (const item of uploadedItems) {
              if (next.some((existing) => existing.id === item.id)) {
                continue;
              }
              next.push(item);
            }
            return next;
          });
        });
        setNotice(uploadedItems.length > 1 ? `已上传 ${uploadedItems.length} 个文件` : '已上传');
      } else if (uploadedItems.length) {
        setNotice('线程已切换');
      }
    } catch (nextError) {
      setError(nextError instanceof Error ? nextError.message : String(nextError));
    } finally {
      setUploadingAttachments(false);
    }
  }

  function handleRemoveAttachment(attachmentId) {
    startTransition(() => {
      setAttachments((current) => current.filter((item) => item.id !== attachmentId));
    });
  }

  const reasoningOptions = resolveReasoningOptions(models, model, defaults.reasoningEffort);
  const selectedModel = summarizeModel(models, model);
  const selectedThreadName = selectedThread?.threadName
    || threads.find((item) => item.id === selectedThreadId)?.threadName
    || '选择线程';
  const bridgeReady = bridgeStatus.reachable !== false;
  const currentMessages = Array.isArray(selectedThread?.messages) ? selectedThread.messages : [];
  const selectedThreadRunning = isThreadBusy(selectedThreadId, selectedThread, activeJob);
  const canCreateThread = access.kind === 'bound'
    && bridgeReady
    && !creatingThread
    && !archivingThread;
  const canArchiveThread = Boolean(selectedThreadId)
    && access.kind === 'bound'
    && bridgeReady
    && !selectedThreadRunning
    && !archivingThread
    && !creatingThread;
  const canRefreshThreads = !refreshingThreads && !creatingThread && !archivingThread;
  const canSend = Boolean(selectedThreadId)
    && access.kind === 'bound'
    && bridgeReady
    && !selectedThreadRunning
    && !sending
    && !uploadingAttachments;
  const visibleError = error ? describeErrorCode(error) : '';
  const visibleKeyError = keyError ? describeErrorCode(keyError) : '';
  const panelBackdropVisible = drawerOpen || keySheetOpen || artifactSheetOpen;
  const runtimeLabel = bridgeReady ? '可用' : '离线';
  const keyLabel = access.kind === 'bound' ? '已锁定' : '密钥';

  return (
    <main
      ref={shellRef}
      className={`cp-codex-mobile-shell ${drawerOpen ? 'drawer-open' : ''} ${composerFocused ? 'composer-focused' : ''}`}
    >
      <aside className="cp-codex-mobile-drawer">
        <div className="cp-codex-mobile-drawer-topbar">
          <div className="cp-codex-mobile-drawer-topbar-row">
            <strong>线程</strong>
            <span className={`cp-codex-mobile-runtime-pill ${bridgeReady ? 'online' : 'offline'}`}>{runtimeLabel}</span>
          </div>
          <div className="cp-codex-mobile-drawer-controls">
            <button
              className="cp-ghost-btn cp-codex-mobile-drawer-action"
              type="button"
              onClick={handleCreateThread}
              disabled={!canCreateThread}
            >
              {creatingThread ? '新建中' : '新建'}
            </button>
            <button
              className="cp-ghost-btn cp-codex-mobile-drawer-action"
              type="button"
              onClick={handleArchiveThread}
              disabled={!canArchiveThread}
            >
              {archivingThread ? '归档中' : '归档'}
            </button>
            <button
              className="cp-ghost-btn cp-codex-mobile-drawer-action"
              type="button"
              onClick={refreshThreads}
              disabled={!canRefreshThreads}
            >
              {refreshingThreads ? '刷新中' : '刷新'}
            </button>
          </div>
        </div>

        <div className="cp-codex-mobile-thread-list">
          {threads.length ? threads.map((item) => (
            <ThreadListItem
              key={item.id}
              active={item.id === selectedThreadId}
              item={item}
              onSelect={loadThread}
              running={isThreadBusy(item.id, selectedThread, activeJob)}
            />
          )) : (
            <div className="cp-codex-mobile-empty-state compact">
              <strong>暂无线程</strong>
            </div>
          )}
        </div>

        <div className="cp-codex-mobile-drawer-footer">
          <button
            className="cp-ghost-btn cp-codex-mobile-drawer-footer-btn"
            type="button"
            onClick={openKeySheet}
          >
            {keyLabel}
          </button>
          {access.kind === 'bound' ? (
            <button
              className="cp-ghost-btn cp-codex-mobile-drawer-footer-btn"
              type="button"
              onClick={handleUnbindDevice}
              disabled={unbinding}
            >
              {unbinding ? '处理中' : '解绑'}
            </button>
          ) : null}
        </div>
      </aside>

      <button
        type="button"
        className={`cp-codex-mobile-backdrop ${panelBackdropVisible ? 'visible' : ''}`}
        aria-label="关闭侧边层"
        onClick={closePanels}
      />

      <div className="cp-codex-mobile-body">
        <header ref={topbarRef} className="cp-codex-mobile-topbar compact">
          <button
            type="button"
            className="cp-codex-mobile-menu-btn"
            onClick={() => {
              setKeySheetOpen(false);
              setArtifactSheetOpen(false);
              setDrawerOpen((current) => !current);
            }}
          >
            线程
          </button>
          <div
            className={`cp-codex-mobile-topbar-thread ${selectedThreadRunning ? 'running' : ''}`}
            title={selectedThreadName}
          >
            {selectedThreadName}
          </div>
          <div className="cp-codex-mobile-topbar-actions compact">
            <span className={`cp-codex-mobile-runtime-pill ${bridgeReady ? 'online' : 'offline'}`}>{runtimeLabel}</span>
            <button
              className="cp-ghost-btn cp-codex-mobile-artifact-btn"
              type="button"
              onClick={() => {
                if (artifactSheetOpen) {
                  setArtifactSheetOpen(false);
                  return;
                }

                void openArtifactSheet();
              }}
            >
              产物
            </button>
            <button
              className="cp-ghost-btn cp-codex-mobile-key-btn"
              type="button"
              onClick={openKeySheet}
            >
              {keyLabel}
            </button>
          </div>
        </header>

        <section className="cp-codex-mobile-stage compact">
          <InlineStatus kind="success">{notice}</InlineStatus>
          <InlineStatus kind="error">{visibleError}</InlineStatus>

          {activeJob && activeJob.sessionId === selectedThreadId ? (
            <div className="cp-codex-mobile-job-inline">
              <span className={`cp-pill ${selectedThreadRunning ? 'warn' : 'ok'}`}>{activeJob.status}</span>
              <span>{activeJob.model}</span>
              <span>{activeJob.reasoningEffort}</span>
            </div>
          ) : null}

          <section className="cp-codex-mobile-chat-panel compact">
            <div ref={messageStreamRef} className="cp-codex-mobile-message-stream">
              {selectedThreadId ? (
                currentMessages.length ? currentMessages.map((item) => (
                  <ConversationMessage
                    key={`${item.id}-${item.timestamp || 'no-time'}`}
                    item={item}
                  />
                )) : (
                  <div className="cp-codex-mobile-empty-state compact">
                    <strong>暂无消息</strong>
                  </div>
                )
              ) : (
                <div className="cp-codex-mobile-empty-state compact">
                  <strong>选择线程</strong>
                </div>
              )}
            </div>
          </section>
        </section>

        <form
          ref={composerRef}
          className="cp-codex-mobile-composer flush"
          onSubmit={handleSendPrompt}
        >
          <div className="cp-codex-mobile-composer-frame flush">
            <input
              ref={fileInputRef}
              className="cp-codex-mobile-file-input"
              type="file"
              multiple
              accept="image/*,.md,.txt,.pdf,.ppt,.pptx,.doc,.docx,.xls,.xlsx,.csv,.json,.zip"
              onChange={handleUploadSelection}
            />
            {controlsOpen ? (
              <div className="cp-codex-mobile-composer-settings compact">
                <div className="cp-codex-mobile-select-grid">
                  <label className="cp-codex-select-field">
                    <span>Model</span>
                    <select
                      value={model}
                      onChange={(event) => {
                        const nextModel = event.target.value;
                        const nextEntry = summarizeModel(models, nextModel);
                        setModel(nextModel);
                        setReasoningEffort(
                          nextEntry?.defaultReasoningLevel
                            || nextEntry?.supportedReasoningLevels?.[0]?.effort
                            || defaults.reasoningEffort
                            || 'medium',
                        );
                      }}
                    >
                      {models.map((item) => (
                        <option key={item.slug} value={item.slug}>
                          {item.displayName}
                        </option>
                      ))}
                    </select>
                  </label>

                  <label className="cp-codex-select-field">
                    <span>Reasoning</span>
                    <select
                      value={reasoningEffort}
                      onChange={(event) => setReasoningEffort(event.target.value)}
                    >
                      {reasoningOptions.map((item) => (
                        <option key={item.effort} value={item.effort}>
                          {item.effort}
                        </option>
                      ))}
                    </select>
                  </label>
                </div>
              </div>
            ) : null}

            {attachments.length ? (
              <div className="cp-codex-mobile-attachment-strip">
                {attachments.map((item) => (
                  <AttachmentChip
                    key={item.id}
                    item={item}
                    onRemove={handleRemoveAttachment}
                  />
                ))}
              </div>
            ) : null}

            <label className="cp-codex-compose-field compact">
              <textarea
                value={prompt}
                onChange={(event) => setPrompt(event.target.value)}
                onFocus={() => {
                  setComposerFocused(true);
                  window.requestAnimationFrame(() => {
                    const stream = messageStreamRef.current;
                    if (!stream) {
                      return;
                    }

                    stream.scrollTo({
                      top: stream.scrollHeight,
                      behavior: 'auto',
                    });
                  });
                }}
                onBlur={() => setComposerFocused(false)}
                placeholder={access.kind === 'bound' ? '继续' : '输入密钥后可发送'}
              />
            </label>

            <div className="cp-codex-mobile-compose-actions compact">
              <button
                className="cp-ghost-btn cp-codex-mobile-toolbar-btn"
                type="button"
                onClick={() => setControlsOpen((current) => !current)}
              >
                {selectedModel?.displayName || model || '模型'}
              </button>
              <button
                className="cp-ghost-btn cp-codex-mobile-toolbar-btn"
                type="button"
                onClick={handleUploadPlaceholder}
                disabled={uploadingAttachments}
              >
                {uploadingAttachments ? '上传中' : '上传'}
              </button>
              <button
                className="cp-ghost-btn cp-codex-mobile-toolbar-btn"
                type="button"
                onClick={handleVoicePlaceholder}
              >
                语音
              </button>
              <button
                className="cp-primary-btn cp-codex-mobile-send-btn"
                type="submit"
                disabled={!canSend}
              >
                {sending
                  ? '发送中'
                  : selectedThreadRunning
                    ? '忙'
                    : access.kind !== 'bound'
                      ? '锁定'
                      : '发送'}
              </button>
            </div>
          </div>
        </form>
      </div>

      {keySheetOpen ? (
        <section className="cp-codex-mobile-key-sheet compact" role="dialog" aria-modal="true">
          <div className="cp-codex-mobile-key-sheet-head compact">
            <strong>{keyLabel}</strong>
            <button
              className="cp-ghost-btn"
              type="button"
              onClick={() => setKeySheetOpen(false)}
            >
              关闭
            </button>
          </div>

          <InlineStatus kind="success">{keyNotice}</InlineStatus>
          <InlineStatus kind="error">{visibleKeyError}</InlineStatus>

          <div className="cp-codex-mobile-key-grid compact">
            <article className="cp-codex-mobile-key-card">
              <span>当前页面</span>
              <strong>{access.kind === 'bound' ? '已锁定' : '只读'}</strong>
            </article>
            {boundDevice ? (
              <article className="cp-codex-mobile-key-card">
                <span>受信终端</span>
                <strong>{boundDevice.label}</strong>
              </article>
            ) : null}
          </div>

          {access.kind === 'bound' ? (
            <div className="cp-codex-mobile-key-actions">
              <button
                className="cp-ghost-btn"
                type="button"
                onClick={handleUnbindDevice}
                disabled={unbinding}
              >
                {unbinding ? '处理中' : '解绑'}
              </button>
            </div>
          ) : (
            <form className="cp-codex-mobile-key-form" onSubmit={handleKeyLock}>
              <label className="cp-login-field compact">
                <input
                  type="password"
                  value={platformKey}
                  onChange={(event) => setPlatformKey(event.target.value)}
                  placeholder="平台密钥"
                  autoFocus
                />
              </label>
              <div className="cp-codex-mobile-key-actions">
                <button className="cp-primary-btn" type="submit" disabled={locking}>
                  {locking ? '校验中' : '锁定'}
                </button>
              </div>
            </form>
          )}
        </section>
      ) : null}

      {artifactSheetOpen ? (
        <section className="cp-codex-mobile-artifact-sheet compact" role="dialog" aria-modal="true">
          <div className="cp-codex-mobile-artifact-sheet-head">
            <strong>产物</strong>
            <div className="cp-codex-mobile-artifact-sheet-actions">
              <button
                className="cp-ghost-btn"
                type="button"
                onClick={() => refreshArtifacts()}
                disabled={loadingArtifacts}
              >
                {loadingArtifacts ? '刷新中' : '刷新'}
              </button>
              <button
                className="cp-ghost-btn"
                type="button"
                onClick={() => setArtifactSheetOpen(false)}
              >
                关闭
              </button>
            </div>
          </div>

          <div className="cp-codex-mobile-artifact-list">
            {artifacts.length ? artifacts.map((item) => (
              <ArtifactListItem key={item.id} item={item} />
            )) : (
              <div className="cp-codex-mobile-empty-state compact">
                <strong>{loadingArtifacts ? '加载中' : '暂无产物'}</strong>
              </div>
            )}
          </div>
        </section>
      ) : null}
    </main>
  );
}
