import 'package:plugin_platform_interface/plugin_platform_interface.dart';

import 'nfc_passport_reader_method_channel.dart';

abstract class NfcPassportReaderPlatform extends PlatformInterface {
  /// Constructs a NfcPassportReaderPlatform.
  NfcPassportReaderPlatform() : super(token: _token);

  static final Object _token = Object();

  static NfcPassportReaderPlatform _instance = MethodChannelNfcPassportReader();

  /// The default instance of [NfcPassportReaderPlatform] to use.
  ///
  /// Defaults to [MethodChannelNfcPassportReader].
  static NfcPassportReaderPlatform get instance => _instance;

  /// Platform-specific implementations should set this with their own
  /// platform-specific class that extends [NfcPassportReaderPlatform] when
  /// they register themselves.
  static set instance(NfcPassportReaderPlatform instance) {
    PlatformInterface.verifyToken(instance, _token);
    _instance = instance;
  }

  // Future<String?> getPlatformVersion() {
  //   throw UnimplementedError('platformVersion() has not been implemented.');
  // }

  Future<Map<String, dynamic>> startNFCReading({
    required String passportNumber,
    required String birthDate,
    required String expirationDate,
  })  {
    throw UnimplementedError('startNFCReading() has not been implemented.');
  }
}
