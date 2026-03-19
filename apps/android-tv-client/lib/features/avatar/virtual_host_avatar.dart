import 'dart:async';
import 'dart:math' as math;

import 'package:flutter/material.dart';

import 'avatar_profile.dart';

enum VirtualHostMood {
  idle,
  listening,
  responding,
  success,
  blocked,
  error,
  drained,
}

class VirtualHostAvatar extends StatefulWidget {
  const VirtualHostAvatar({
    super.key,
    required this.avatarLabel,
    required this.subtitle,
    required this.executionStatus,
    required this.isHandlingVoice,
    this.compact = false,
    this.isLowCapacity = false,
    this.profile,
  });

  final String avatarLabel;
  final String subtitle;
  final String executionStatus;
  final bool isHandlingVoice;
  final bool compact;
  final bool isLowCapacity;
  final AvatarProfile? profile;

  @override
  State<VirtualHostAvatar> createState() => _VirtualHostAvatarState();
}

class _VirtualHostAvatarState extends State<VirtualHostAvatar>
    with SingleTickerProviderStateMixin {
  late final AnimationController _pulseController;
  Timer? _blinkTimer;
  bool _isBlinking = false;

  @override
  void initState() {
    super.initState();
    _pulseController = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 1800),
    )..repeat();
    _scheduleBlink();
  }

  @override
  void dispose() {
    _blinkTimer?.cancel();
    _pulseController.dispose();
    super.dispose();
  }

  void _scheduleBlink() {
    _blinkTimer?.cancel();
    _blinkTimer = Timer(const Duration(seconds: 3), () {
      if (!mounted) {
        return;
      }
      setState(() {
        _isBlinking = true;
      });
      Timer(const Duration(milliseconds: 170), () {
        if (!mounted) {
          return;
        }
        setState(() {
          _isBlinking = false;
        });
        _scheduleBlink();
      });
    });
  }

  VirtualHostMood get _mood {
    if (widget.isLowCapacity) {
      return VirtualHostMood.drained;
    }
    final status = widget.executionStatus.toLowerCase();
    if (widget.isHandlingVoice || status.contains('listening')) {
      return VirtualHostMood.listening;
    }
    if (status.contains('blocked')) {
      return VirtualHostMood.blocked;
    }
    if (status.contains('failed') || status.contains('error')) {
      return VirtualHostMood.error;
    }
    if (status.contains('executed') ||
        status.contains('confirmed') ||
        status.contains('ready')) {
      return VirtualHostMood.success;
    }
    if (status.contains('chat') ||
        status.contains('refreshing') ||
        status.contains('creating') ||
        status.contains('submitting')) {
      return VirtualHostMood.responding;
    }
    return VirtualHostMood.idle;
  }

  Color get _primaryColor {
    final configured = _tryParseColor(widget.profile?.primaryColorHex);
    if (configured != null) {
      return configured;
    }
    switch (_mood) {
      case VirtualHostMood.listening:
        return const Color(0xFF6AE6D8);
      case VirtualHostMood.responding:
        return const Color(0xFF7CC6FE);
      case VirtualHostMood.success:
        return const Color(0xFF7EF0A5);
      case VirtualHostMood.blocked:
        return const Color(0xFFFFC857);
      case VirtualHostMood.error:
        return const Color(0xFFFF8F8F);
      case VirtualHostMood.idle:
        return const Color(0xFFB0D9FF);
      case VirtualHostMood.drained:
        return const Color(0xFFCDB8A0);
    }
  }

  Color get _secondaryColor {
    final configured = _tryParseColor(widget.profile?.secondaryColorHex);
    if (configured != null) {
      return configured;
    }
    switch (_mood) {
      case VirtualHostMood.listening:
        return const Color(0xFF12656A);
      case VirtualHostMood.responding:
        return const Color(0xFF1C4F8A);
      case VirtualHostMood.success:
        return const Color(0xFF19563F);
      case VirtualHostMood.blocked:
        return const Color(0xFF725317);
      case VirtualHostMood.error:
        return const Color(0xFF6B2336);
      case VirtualHostMood.idle:
        return const Color(0xFF294A6B);
      case VirtualHostMood.drained:
        return const Color(0xFF57473E);
    }
  }

  double get _subtitleEnergy {
    final trimmed = widget.subtitle.trim();
    if (trimmed.isEmpty) {
      return 0.18;
    }
    return trimmed.length.clamp(8, 96) / 96;
  }

  double get _mouthWidthFactor {
    switch (_mood) {
      case VirtualHostMood.listening:
        return 0.22;
      case VirtualHostMood.responding:
        return 0.28 + (_subtitleEnergy * 0.16);
      case VirtualHostMood.success:
        return 0.24;
      case VirtualHostMood.blocked:
        return 0.18;
      case VirtualHostMood.error:
        return 0.16;
      case VirtualHostMood.idle:
        return 0.20;
      case VirtualHostMood.drained:
        return 0.14;
    }
  }

  double get _mouthHeight {
    switch (_mood) {
      case VirtualHostMood.listening:
        return 16;
      case VirtualHostMood.responding:
        return 20;
      case VirtualHostMood.success:
        return 14;
      case VirtualHostMood.blocked:
        return 10;
      case VirtualHostMood.error:
        return 8;
      case VirtualHostMood.idle:
        return 12;
      case VirtualHostMood.drained:
        return 7;
    }
  }

  @override
  Widget build(BuildContext context) {
    return AnimatedContainer(
      duration: const Duration(milliseconds: 350),
      height: widget.compact ? 150 : 220,
      padding: EdgeInsets.all(widget.compact ? 14 : 18),
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(28),
        gradient: LinearGradient(
          colors: [
            _secondaryColor.withValues(alpha: 0.94),
            const Color(0xFF0B1726),
          ],
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
        ),
        border: Border.all(color: _primaryColor.withValues(alpha: 0.32)),
      ),
      child: LayoutBuilder(
        builder: (context, constraints) {
          final faceSize = math.min(
            constraints.maxHeight - (widget.compact ? 26 : 34),
            widget.compact ? 104.0 : 150.0,
          );
          return Stack(
            children: [
              Align(
                child: AnimatedBuilder(
                  animation: _pulseController,
                  builder: (context, child) {
                    final pulse = Curves.easeInOut.transform(_pulseController.value);
                    final waveOpacity = 0.12 + (pulse * 0.14);
                    return SizedBox(
                      width: faceSize + (widget.compact ? 50 : 72),
                      height: faceSize + (widget.compact ? 50 : 72),
                      child: Stack(
                        alignment: Alignment.center,
                        children: [
                          Container(
                            width: faceSize + (pulse * 46),
                            height: faceSize + (pulse * 46),
                            decoration: BoxDecoration(
                              shape: BoxShape.circle,
                              color: _primaryColor.withValues(alpha: waveOpacity),
                            ),
                          ),
                          Container(
                            width: faceSize + 26 + (pulse * 18),
                            height: faceSize + 26 + (pulse * 18),
                            decoration: BoxDecoration(
                              shape: BoxShape.circle,
                              border: Border.all(
                                color: Colors.white.withValues(alpha: 0.12 + pulse * 0.08),
                                width: 1.4,
                              ),
                            ),
                          ),
                          child!,
                        ],
                      ),
                    );
                  },
                  child: _AvatarFace(
                    size: faceSize,
                    primaryColor: _primaryColor,
                    secondaryColor: _secondaryColor,
                    isBlinking: _isBlinking,
                    mood: _mood,
                    mouthHeight: _mouthHeight,
                    mouthWidthFactor: _mouthWidthFactor,
                  ),
                ),
              ),
            ],
          );
        },
      ),
    );
  }

  Color? _tryParseColor(String? hex) {
    if (hex == null || hex.isEmpty) {
      return null;
    }

    final sanitized = hex.replaceFirst('#', '');
    if (sanitized.length != 6 && sanitized.length != 8) {
      return null;
    }

    final buffer = StringBuffer();
    if (sanitized.length == 6) {
      buffer.write('ff');
    }
    buffer.write(sanitized);
    try {
      return Color(int.parse(buffer.toString(), radix: 16));
    } on FormatException {
      return null;
    }
  }
}

class _AvatarFace extends StatelessWidget {
  const _AvatarFace({
    required this.size,
    required this.primaryColor,
    required this.secondaryColor,
    required this.isBlinking,
    required this.mood,
    required this.mouthHeight,
    required this.mouthWidthFactor,
  });

  final double size;
  final Color primaryColor;
  final Color secondaryColor;
  final bool isBlinking;
  final VirtualHostMood mood;
  final double mouthHeight;
  final double mouthWidthFactor;

  @override
  Widget build(BuildContext context) {
    final eyeHeight = isBlinking ? 3.5 : 14.0;
    final mouthColor = switch (mood) {
      VirtualHostMood.blocked => const Color(0xFFFFE08A),
      VirtualHostMood.error => const Color(0xFFFFBAB1),
      _ => Colors.white,
    };

    return AnimatedContainer(
      duration: const Duration(milliseconds: 250),
      width: size,
      height: size,
      decoration: BoxDecoration(
        shape: BoxShape.circle,
        gradient: LinearGradient(
          colors: [primaryColor, secondaryColor],
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
        ),
        boxShadow: [
          BoxShadow(
            color: primaryColor.withValues(alpha: 0.26),
            blurRadius: 34,
            spreadRadius: 6,
          ),
        ],
      ),
      child: Stack(
        children: [
          Positioned(
            top: size * 0.18,
            left: size * 0.22,
            child: Container(
              width: size * 0.2,
              height: size * 0.2,
              decoration: BoxDecoration(
                shape: BoxShape.circle,
                color: Colors.white.withValues(alpha: 0.20),
              ),
            ),
          ),
          Center(
            child: Column(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                Row(
                  mainAxisAlignment: MainAxisAlignment.center,
                  children: [
                    _Eye(
                      width: size * 0.14,
                      height: eyeHeight,
                      color: Colors.white,
                    ),
                    SizedBox(width: size * 0.12),
                    _Eye(
                      width: size * 0.14,
                      height: eyeHeight,
                      color: Colors.white,
                    ),
                  ],
                ),
                SizedBox(height: size * 0.14),
                AnimatedContainer(
                  duration: const Duration(milliseconds: 260),
                  width: size * mouthWidthFactor,
                  height: mouthHeight,
                  decoration: BoxDecoration(
                    color: mouthColor.withValues(alpha: 0.9),
                    borderRadius: BorderRadius.circular(999),
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

class _Eye extends StatelessWidget {
  const _Eye({
    required this.width,
    required this.height,
    required this.color,
  });

  final double width;
  final double height;
  final Color color;

  @override
  Widget build(BuildContext context) {
    return AnimatedContainer(
      duration: const Duration(milliseconds: 140),
      width: width,
      height: height,
      decoration: BoxDecoration(
        color: color,
        borderRadius: BorderRadius.circular(999),
      ),
    );
  }
}
