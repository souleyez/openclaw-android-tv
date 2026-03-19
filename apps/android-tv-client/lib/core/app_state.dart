class AppState {
  const AppState({
    required this.selectedLanguage,
    required this.selectedAvatar,
    required this.subscriptionLabel,
    required this.tokenBalance,
    required this.backgroundStandbyEnabled,
  });

  final String selectedLanguage;
  final String selectedAvatar;
  final String subscriptionLabel;
  final int tokenBalance;
  final bool backgroundStandbyEnabled;

  AppState copyWith({
    String? selectedLanguage,
    String? selectedAvatar,
    String? subscriptionLabel,
    int? tokenBalance,
    bool? backgroundStandbyEnabled,
  }) {
    return AppState(
      selectedLanguage: selectedLanguage ?? this.selectedLanguage,
      selectedAvatar: selectedAvatar ?? this.selectedAvatar,
      subscriptionLabel: subscriptionLabel ?? this.subscriptionLabel,
      tokenBalance: tokenBalance ?? this.tokenBalance,
      backgroundStandbyEnabled:
          backgroundStandbyEnabled ?? this.backgroundStandbyEnabled,
    );
  }
}
