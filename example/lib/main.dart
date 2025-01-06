import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'dart:async';

import 'package:flutter/services.dart';
import 'package:nfc_passport_reader/nfc_passport_reader.dart';
import 'package:nfc_passport_reader/nfc_passport_reader_method_channel.dart';

void main() {
  runApp(const MyApp());
}

class MyApp extends StatefulWidget {
  const MyApp({super.key});

  @override
  State<MyApp> createState() => _MyAppState();
}

class _MyAppState extends State<MyApp> {
  String _platformVersion = 'Unknown';
  final _nfcPassportReaderPlugin = NfcPassportReader();

  @override
  void initState() {
    super.initState();
    initPlatformState();
  }

  // Platform messages are asynchronous, so we initialize in an async method.
  Future<void> initPlatformState() async {
    String platformVersion;
    // Platform messages may fail, so we use a try/catch PlatformException.
    // We also handle the message potentially returning null.
    // try {
    //   platformVersion =
    //       await _nfcPassportReaderPlugin.getPlatformVersion() ?? 'Unknown platform version';
    // } on PlatformException {
    //   platformVersion = 'Failed to get platform version.';
    // }

    print("############ reading tag");

    // readnfcTag();

    // If the widget was removed from the tree while the asynchronous platform
    // message was in flight, we want to discard the reply rather than calling
    // setState to update our non-existent appearance.
    if (!mounted) return;

    // setState(() {
    //   _platformVersion = platformVersion;
    // });
  }

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      home: Scaffold(
        appBar: AppBar(
          title: const Text('Plugin example app'),
        ),
        body: NfcPassportReaderWidget(
          passportNumber: 'AK0690654',
          birthDate: '1995-04-14',
          expirationDate: '2030-01-26',
          loadingWidget: const Center(
            child: Column(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                CircularProgressIndicator(),
                SizedBox(height: 16),
                Text('Place your phone on the passport\'s chip'),
              ],
            ),
          ),
          onSuccess: (data) => PassportDataDisplay(data: PassportData.fromMap(data)),
          onError: (error) => ErrorDisplay(error: error),
        ),
      ),
    );
  }


}



class PassportDataDisplay extends StatelessWidget {
  final PassportData data;
  const PassportDataDisplay({super.key,  required this.data});


  @override
  Widget build(BuildContext context) {
    return ListView(
      children: [
        PassportImageWidget(image: data.photoBase64?.trim(), width: 200, height: 200, fit: BoxFit.contain,),
        ListTile(
          title: const Text('First Name'),
          subtitle: Text(data.firstName),
        ),
        ListTile(
          title: const Text('Last Name'),
          subtitle: Text(data.lastName),
        ),
        ListTile(
          title: const Text('Gender'),
          subtitle: Text(data.gender),
        ),
        ListTile(
          title: const Text('State'),
          subtitle: Text(data.state),
        ),
        ListTile(
          title: const Text('Nationality'),
          subtitle: Text(data.nationality),
        ),

        ListTile(
          title: const Text('Personal Number'),
          subtitle: Text(data.personalNumber),
        ),

        ListTile(
          title: const Text('Place Of birth'),
          subtitle: Text(data.placeOfBirth),
        ),

        ListTile(
          title: const Text('Residence'),
          subtitle: Text(data.residence),
        ),

        ListTile(
          title: const Text('Phone Number'),
          subtitle: Text(data.phoneNumber),
        ),
        ListTile(
          title: const Text('Proffession'),
          subtitle: Text(data.profession),
        ),
        PassportImageWidget(image: data.signature.trim(), width: 300, height: 200, fit: BoxFit.contain,),

      ],
    );
  }
}

class ErrorDisplay extends StatelessWidget {
  final NfcPassportError error;
  const ErrorDisplay({super.key, required this.error});

  @override
  Widget build(BuildContext context) {
    return Column(
      mainAxisAlignment: MainAxisAlignment.center,
      children: [
        Text("${error.code}"),
        Text("${error.message} - ${error.details}")
      ],
    );
  }
}


