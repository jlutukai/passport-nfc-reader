import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:nfc_passport_reader/nfc_passport_reader_method_channel.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  MethodChannelNfcPassportReader platform = MethodChannelNfcPassportReader();
  const MethodChannel channel = MethodChannel('nfc_passport_reader');

  setUp(() {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger.setMockMethodCallHandler(
      channel,
      (MethodCall methodCall) async {
        return '42';
      },
    );
  });

  tearDown(() {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger.setMockMethodCallHandler(channel, null);
  });

  test('getPlatformVersion', () async {
    expect("42", '42');
  });
}
