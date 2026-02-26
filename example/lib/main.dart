import 'dart:async';

import 'package:callbundle/callbundle.dart';
import 'package:flutter/material.dart';

void main() {
  WidgetsFlutterBinding.ensureInitialized();
  runApp(const CallBundleExampleApp());
}

class CallBundleExampleApp extends StatelessWidget {
  const CallBundleExampleApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'CallBundle Example',
      theme: ThemeData(
        colorSchemeSeed: Colors.deepPurple,
        useMaterial3: true,
      ),
      home: const HomePage(),
    );
  }
}

class HomePage extends StatefulWidget {
  const HomePage({super.key});

  @override
  State<HomePage> createState() => _HomePageState();
}

class _HomePageState extends State<HomePage> {
  final List<String> _eventLog = [];
  StreamSubscription<NativeCallEvent>? _eventSub;
  String? _voipToken;
  bool _isConfigured = false;

  @override
  void initState() {
    super.initState();
    _initCallBundle();
  }

  Future<void> _initCallBundle() async {
    // Listen for call events
    _eventSub = CallBundle.onEvent.listen((event) {
      setState(() {
        _eventLog.insert(
          0,
          '[${event.type.name}] callId=${event.callId} '
          'userInitiated=${event.isUserInitiated}',
        );
      });
    });

    // Configure the plugin
    await CallBundle.configure(
      const NativeCallConfig(
        appName: 'CallBundle Example',
        android: AndroidCallConfig(
          phoneAccountLabel: 'CallBundle',
          notificationChannelName: 'Incoming Calls',
        ),
        ios: IosCallConfig(
          supportsVideo: false,
          maximumCallGroups: 1,
          maximumCallsPerCallGroup: 1,
          includesCallsInRecents: true,
        ),
      ),
    );

    setState(() {
      _isConfigured = true;
    });

    // Request permissions
    final permissions = await CallBundle.requestPermissions();
    _addLog('Permissions: ${permissions.toMap()}');

    // Get VoIP token (iOS only)
    final token = await CallBundle.getVoipToken();
    if (token != null) {
      setState(() => _voipToken = token);
      _addLog('VoIP Token: ${token.substring(0, 8)}...');
    }
  }

  void _addLog(String message) {
    setState(() {
      _eventLog.insert(0, message);
    });
  }

  Future<void> _simulateIncomingCall() async {
    final callId = DateTime.now().millisecondsSinceEpoch.toString();
    await CallBundle.showIncomingCall(
      NativeCallParams(
        callId: callId,
        callerName: 'John Doe',
        handle: '+1 234 567 8900',
        callType: NativeCallType.voice,
        android: const AndroidCallParams(),
        ios: const IosCallParams(),
      ),
    );
    _addLog('Showing incoming call: $callId');
  }

  Future<void> _simulateOutgoingCall() async {
    final callId = DateTime.now().millisecondsSinceEpoch.toString();
    await CallBundle.showOutgoingCall(
      NativeCallParams(
        callId: callId,
        callerName: 'Jane Smith',
        handle: '+1 987 654 3210',
        callType: NativeCallType.voice,
        android: const AndroidCallParams(),
        ios: const IosCallParams(),
      ),
    );
    _addLog('Starting outgoing call: $callId');
  }

  Future<void> _endAllCalls() async {
    await CallBundle.endAllCalls();
    _addLog('Ended all calls');
  }

  Future<void> _getActiveCalls() async {
    final calls = await CallBundle.getActiveCalls();
    _addLog('Active calls: ${calls.length}');
    for (final call in calls) {
      _addLog('  - ${call.callerName} (${call.state.name})');
    }
  }

  @override
  void dispose() {
    _eventSub?.cancel();
    CallBundle.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('CallBundle Example'),
        actions: [
          if (_voipToken != null)
            const Padding(
              padding: EdgeInsets.only(right: 8),
              child: Icon(Icons.verified, color: Colors.green),
            ),
        ],
      ),
      body: Column(
        children: [
          // Status
          Container(
            width: double.infinity,
            padding: const EdgeInsets.all(12),
            color: _isConfigured ? Colors.green.shade50 : Colors.orange.shade50,
            child: Text(
              _isConfigured ? 'Plugin configured' : 'Configuring...',
              style: TextStyle(
                color: _isConfigured
                    ? Colors.green.shade700
                    : Colors.orange.shade700,
                fontWeight: FontWeight.w600,
              ),
            ),
          ),

          // Action buttons
          Padding(
            padding: const EdgeInsets.all(16),
            child: Wrap(
              spacing: 8,
              runSpacing: 8,
              children: [
                FilledButton.icon(
                  onPressed: _isConfigured ? _simulateIncomingCall : null,
                  icon: const Icon(Icons.call_received),
                  label: const Text('Incoming Call'),
                ),
                FilledButton.icon(
                  onPressed: _isConfigured ? _simulateOutgoingCall : null,
                  icon: const Icon(Icons.call_made),
                  label: const Text('Outgoing Call'),
                ),
                OutlinedButton.icon(
                  onPressed: _isConfigured ? _endAllCalls : null,
                  icon: const Icon(Icons.call_end),
                  label: const Text('End All'),
                ),
                OutlinedButton.icon(
                  onPressed: _isConfigured ? _getActiveCalls : null,
                  icon: const Icon(Icons.list),
                  label: const Text('Active Calls'),
                ),
              ],
            ),
          ),

          const Divider(),

          // Event log
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
            child: Row(
              children: [
                const Text(
                  'Event Log',
                  style: TextStyle(fontWeight: FontWeight.bold, fontSize: 16),
                ),
                const Spacer(),
                TextButton(
                  onPressed: () => setState(() => _eventLog.clear()),
                  child: const Text('Clear'),
                ),
              ],
            ),
          ),

          Expanded(
            child: _eventLog.isEmpty
                ? const Center(
                    child: Text(
                      'No events yet.\nTap a button above to simulate a call.',
                      textAlign: TextAlign.center,
                      style: TextStyle(color: Colors.grey),
                    ),
                  )
                : ListView.builder(
                    itemCount: _eventLog.length,
                    padding: const EdgeInsets.symmetric(horizontal: 16),
                    itemBuilder: (context, index) {
                      return Padding(
                        padding: const EdgeInsets.symmetric(vertical: 2),
                        child: Text(
                          _eventLog[index],
                          style: const TextStyle(
                            fontFamily: 'monospace',
                            fontSize: 12,
                          ),
                        ),
                      );
                    },
                  ),
          ),
        ],
      ),
    );
  }
}
