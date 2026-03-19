class ConversationTurn {
  const ConversationTurn({
    required this.speaker,
    required this.text,
    required this.metadata,
  });

  final String speaker;
  final String text;
  final String metadata;
}
