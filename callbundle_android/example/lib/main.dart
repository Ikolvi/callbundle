import 'package:flutter/material.dart';

void main() {
  runApp(const MyApp());
}

class MyApp extends StatelessWidget {
  const MyApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      home: Scaffold(
        appBar: AppBar(
          title: const Text('CallBundle Android Example'),
        ),
        body: const Center(
          child: Text('CallBundle Android plugin loaded.'),
        ),
      ),
    );
  }
}
