export function useLeaseVisualState(params: {
  subscriptionIsActive: boolean;
  subscriptionPreviewEnabled: boolean;
  hasLease: boolean;
  interactionPhase: 'idle' | 'active_local' | 'active_leased';
}) {
  return {
    showModelGlow:
      params.subscriptionIsActive ||
      params.subscriptionPreviewEnabled ||
      params.hasLease ||
      params.interactionPhase === 'active_leased',
  };
}
