import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';

import 'nfc_passport_reader_platform_interface.dart';

/// An implementation of [NfcPassportReaderPlatform] that uses method channels.
class MethodChannelNfcPassportReader extends NfcPassportReaderPlatform {
  /// The method channel used to interact with the native platform.
  @visibleForTesting
  final methodChannel = const MethodChannel('nfc_passport_reader');

  // @override
  // Future<String?> getPlatformVersion() async {
  //   final version = await methodChannel.invokeMethod<String>('getPlatformVersion');
  //   return version;
  // }

   @override
  Future<Map<String, dynamic>> startNFCReading({
    required String passportNumber,
    required String birthDate,
    required String expirationDate,
  }) async {
    try {
      // final Map<String, dynamic> result = await methodChannel.invokeMethod(
      //   'startNFCReading',
      //   {
      //     'passportNumber': passportNumber,
      //     'birthDate': birthDate,
      //     'expirationDate': expirationDate,
      //   },
      // );
      // return result;

      final result = await methodChannel.invokeMethod<Map>('startNFCReading', {
        'passportNumber': passportNumber,
        'birthDate': birthDate,
        'expirationDate': expirationDate,
      });

      // Properly cast the result
      if (result == null) {
        throw PlatformException(
          code: 'NULL_RESULT',
          message: 'The platform returned a null result',
        );
      }

      // Convert the result to Map<String, dynamic>
      return Map<String, dynamic>.from(result);
    } on PlatformException catch (e) {
      throw PassportReaderException(
        code: e.code,
        message: e.message ?? 'Unknown error occurred',
        details: e.details,
      );
    }
  }
}


class PassportReaderException implements Exception {
  final String code;
  final String message;
  final dynamic details;

  PassportReaderException({
    required this.code,
    required this.message,
    this.details,
  });

  @override
  String toString() => 'PassportReaderException($code): $message';
}