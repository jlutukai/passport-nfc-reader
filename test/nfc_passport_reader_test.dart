import 'package:flutter_test/flutter_test.dart';
import 'package:nfc_passport_reader/nfc_passport_reader.dart';
import 'package:nfc_passport_reader/nfc_passport_reader_platform_interface.dart';
import 'package:nfc_passport_reader/nfc_passport_reader_method_channel.dart';
import 'package:plugin_platform_interface/plugin_platform_interface.dart';

class MockNfcPassportReaderPlatform
    with MockPlatformInterfaceMixin
    implements NfcPassportReaderPlatform {

  @override
  Future<String?> getPlatformVersion() => Future.value('42');

  @override
  Future<Map<String, dynamic>> startNFCReading({required String passportNumber, required String birthDate, required String
  expirationDate}) {
    return Future.value({

    });
  }
}

void main() {
  final NfcPassportReaderPlatform initialPlatform = NfcPassportReaderPlatform.instance;

  test('$MethodChannelNfcPassportReader is the default instance', () {
    expect(initialPlatform, isInstanceOf<MethodChannelNfcPassportReader>());
  });

  test('getPlatformVersion', () async {
    NfcPassportReader nfcPassportReaderPlugin = NfcPassportReader();
    MockNfcPassportReaderPlatform fakePlatform = MockNfcPassportReaderPlatform();
    NfcPassportReaderPlatform.instance = fakePlatform;

    expect("42", '42');
  });
}
