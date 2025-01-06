//
// import 'nfc_passport_reader_platform_interface.dart';
//
// class NfcPassportReader {
//   // Future<String?> getPlatformVersion() {
//   //   return NfcPassportReaderPlatform.instance.getPlatformVersion();
//   // }
//
//   Future<Map<String, dynamic>> startNFCReading({
//     required String passportNumber,
//     required String birthDate,
//     required String expirationDate,
//   })  {
//     return NfcPassportReaderPlatform.instance.startNFCReading(passportNumber: passportNumber, birthDate: birthDate, expirationDate: expirationDate);
//   }
// }

// lib/nfc_passport_reader.dart

import 'dart:async';
import 'dart:convert';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import 'nfc_passport_reader_platform_interface.dart';

enum NfcPassportState { ready, reading, success, error }

class NfcPassportError {
  final String code;
  final String message;
  final dynamic details;

  NfcPassportError({
    required this.code,
    required this.message,
    this.details,
  });

  @override
  String toString() => 'NfcPassportError($code): $message';
}

class NfcPassportReader extends ChangeNotifier {
  // static const MethodChannel _channel = MethodChannel('nfc_passport_reader');

  NfcPassportState _state = NfcPassportState.ready;
  NfcPassportError? _error;
  Map<String, dynamic>? _passportData;

  NfcPassportState get state => _state;

  NfcPassportError? get error => _error;

  Map<String, dynamic>? get passportData => _passportData;

  Future<void> startNFCReading({
    required String passportNumber,
    required String birthDate,
    required String expirationDate,
  }) async {
    try {
      _state = NfcPassportState.reading;
      _error = null;
      _passportData = null;
      notifyListeners();

      final Map<String, dynamic> result =
          await NfcPassportReaderPlatform.instance.startNFCReading(
              passportNumber: passportNumber,
              birthDate: birthDate,
              expirationDate: expirationDate);


      _passportData = result;
      _state = NfcPassportState.success;
      notifyListeners();
    } on PlatformException catch (e) {
      _error = NfcPassportError(
        code: e.code,
        message: e.message ?? 'Unknown error occurred',
        details: e.details,
      );
      _state = NfcPassportState.error;
      notifyListeners();
      rethrow;
    } catch (e) {
      _error = NfcPassportError(
        code: 'UNKNOWN',
        message: e.toString(),
      );
      _state = NfcPassportState.error;
      notifyListeners();
      rethrow;
    }
  }

  void reset() {
    _state = NfcPassportState.ready;
    _error = null;
    _passportData = null;
    notifyListeners();
  }
}

// Example widget to handle the UI states
class NfcPassportReaderWidget extends StatefulWidget {
  final String passportNumber;
  final String birthDate;
  final String expirationDate;
  final Widget Function(Map<String, dynamic> data) onSuccess;
  final Widget Function(NfcPassportError error)? onError;
  final Widget? loadingWidget;

  const NfcPassportReaderWidget({
    Key? key,
    required this.passportNumber,
    required this.birthDate,
    required this.expirationDate,
    required this.onSuccess,
    this.onError,
    this.loadingWidget,
  }) : super(key: key);

  @override
  State<NfcPassportReaderWidget> createState() =>
      _NfcPassportReaderWidgetState();
}

class _NfcPassportReaderWidgetState extends State<NfcPassportReaderWidget> {
  final _reader = NfcPassportReader();

  @override
  void initState() {
    super.initState();
    _startReading();
  }

  Future<void> _startReading() async {
    try {
      await _reader.startNFCReading(
        passportNumber: widget.passportNumber,
        birthDate: widget.birthDate,
        expirationDate: widget.expirationDate,
      );
    } catch (e) {
      // Error is handled by the widget builder
    }
  }

  @override
  Widget build(BuildContext context) {
    return ListenableBuilder(
      listenable: _reader,
      builder: (context, _) {
        switch (_reader.state) {
          case NfcPassportState.reading:
            return widget.loadingWidget ??
                const Center(
                  child: Column(
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: [
                      CircularProgressIndicator(),
                      SizedBox(height: 16),
                      Text(
                          'Reading passport...\nPlease hold your phone against the passport'),
                    ],
                  ),
                );

          case NfcPassportState.success:
            if (_reader.passportData != null) {
              return widget.onSuccess(_reader.passportData!);
            }
            return const SizedBox.shrink();

          case NfcPassportState.error:
            if (_reader.error != null && widget.onError != null) {
              return widget.onError!(_reader.error!);
            }
            return Center(
              child: Column(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  const Icon(Icons.error_outline, color: Colors.red, size: 48),
                  const SizedBox(height: 16),
                  Text(_reader.error?.message ?? 'Unknown error occurred'),
                  TextButton(
                    onPressed: _startReading,
                    child: const Text('Try Again'),
                  ),
                ],
              ),
            );

          case NfcPassportState.ready:
            return const SizedBox.shrink();
        }
      },
    );
  }

  @override
  void dispose() {
    _reader.dispose();
    super.dispose();
  }
}



class PassportImageWidget extends StatelessWidget {
  final String? image;
  final double? width;
  final double? height;
  final BoxFit fit;
  final BorderRadius? borderRadius;

  const PassportImageWidget({
    super.key,
    required this.image,
    this.width,
    this.height,
    this.fit = BoxFit.cover,
    this.borderRadius,
  });

  @override
  Widget build(BuildContext context) {
    if ((image??"").isEmpty) {
      return _buildPlaceholder();
    }

    return ClipRRect(
      borderRadius: borderRadius ?? BorderRadius.circular(8),
      child: Container(
        width: width,
        height: height,
        decoration: BoxDecoration(
          color: Colors.grey[200],
          borderRadius: borderRadius ?? BorderRadius.circular(8),
        ),
        child: Image.memory(
          base64Decode(image!),
          fit: fit,
          width: width,
          height: height,
          errorBuilder: (context, error, stackTrace) {
            debugPrint('Error loading passport image: $error');
            return _buildErrorWidget();
          },
          // loadingBuilder: (context, child, loadingProgress) {
          //   if (loadingProgress == null) {
          //     return child;
          //   }
          //   return _buildLoadingWidget(loadingProgress);
          // },
        ),
      ),
    );
  }

  Widget _buildPlaceholder() {
    return Container(
      width: width,
      height: height,
      decoration: BoxDecoration(
        color: Colors.grey[200],
        borderRadius: borderRadius ?? BorderRadius.circular(8),
      ),
      child: Center(
        child: Icon(
          Icons.person_outline,
          size: (width ?? 100) * 0.5,
          color: Colors.grey[400],
        ),
      ),
    );
  }

  Widget _buildErrorWidget() {
    return Container(
      width: width,
      height: height,
      decoration: BoxDecoration(
        color: Colors.grey[200],
        borderRadius: borderRadius ?? BorderRadius.circular(8),
      ),
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Icon(
            Icons.error_outline,
            size: 24,
            color: Colors.red[400],
          ),
          const SizedBox(height: 8),
          Text(
            'Failed to load image',
            style: TextStyle(
              fontSize: 12,
              color: Colors.grey[600],
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildLoadingWidget(ImageChunkEvent loadingProgress) {
    return Container(
      width: width,
      height: height,
      decoration: BoxDecoration(
        color: Colors.grey[200],
        borderRadius: borderRadius ?? BorderRadius.circular(8),
      ),
      child: Center(
        child: CircularProgressIndicator(
          value: loadingProgress.expectedTotalBytes != null
              ? loadingProgress.cumulativeBytesLoaded /
              loadingProgress.expectedTotalBytes!
              : null,
        ),
      ),
    );
  }
}


class PassportData {
  final String firstName;
  final String lastName;
  final String gender;
  final String state;
  final String nationality;
  final bool passiveAuthSuccess;
  final bool chipAuthSuccess;
  final String? photoBase64;
  // DG11 fields
  final String fullName;
  final List<String> otherNames;
  final String personalNumber;
  final String placeOfBirth;
  final String residence;
  final String phoneNumber;
  final String signature;
  final String profession;

  const PassportData({
    required this.firstName,
    required this.lastName,
    required this.gender,
    required this.state,
    required this.nationality,
    required this.passiveAuthSuccess,
    required this.chipAuthSuccess,
    this.photoBase64,
    this.fullName = '',
    this.otherNames = const [],
    this.personalNumber = '',
    this.placeOfBirth = '',
    this.residence = '',
    this.phoneNumber = '',
    this.signature = '',
    this.profession = '',
  });

  factory PassportData.fromMap(Map<String, dynamic> map) {
    return PassportData(
      firstName: map['firstName'] as String? ?? '',
      lastName: map['lastName'] as String? ?? '',
      gender: map['gender'] as String? ?? '',
      state: map['state'] as String? ?? '',
      nationality: map['nationality'] as String? ?? '',
      passiveAuthSuccess: map['passiveAuthSuccess'] as bool? ?? false,
      chipAuthSuccess: map['chipAuthSuccess'] as bool? ?? false,
      photoBase64: map['photo'] as String?,
      fullName: map['fullName'] as String? ?? '',
      otherNames: (map['otherNames'] as List<dynamic>?)?.cast<String>() ?? [],
      personalNumber: map['personalNumber'] as String? ?? '',
      placeOfBirth: map['placeOfBirth'] as String? ?? '',
      residence: map['residence'] as String? ?? '',
      phoneNumber: map['phoneNumber'] as String? ?? '',
      signature: map['signature'] as String? ?? '',
      profession: map['profession'] as String? ?? '',
    );
  }
}