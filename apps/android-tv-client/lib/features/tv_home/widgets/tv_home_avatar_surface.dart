import 'package:flutter/material.dart';

import '../../avatar/avatar_profile.dart';
import '../../avatar/virtual_host_avatar.dart';

class TvHomeAvatarSurface extends StatelessWidget {
  const TvHomeAvatarSurface({
    super.key,
    required this.selectedAvatar,
    required this.subtitle,
    required this.executionStatus,
    required this.isHandlingVoice,
    required this.isLowCapacity,
    required this.profile,
    required this.onTap,
  });

  final String selectedAvatar;
  final String subtitle;
  final String executionStatus;
  final bool isHandlingVoice;
  final bool isLowCapacity;
  final AvatarProfile? profile;
  final VoidCallback? onTap;

  @override
  Widget build(BuildContext context) {
    return FocusableActionDetector(
      child: InkWell(
        canRequestFocus: true,
        borderRadius: BorderRadius.circular(24),
        onTap: onTap,
        child: SizedBox(
          width: 190,
          child: VirtualHostAvatar(
            avatarLabel: selectedAvatar,
            subtitle: subtitle,
            executionStatus: executionStatus,
            isHandlingVoice: isHandlingVoice,
            compact: true,
            isLowCapacity: isLowCapacity,
            profile: profile,
          ),
        ),
      ),
    );
  }
}
