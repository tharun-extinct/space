import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

void main() => runApp(const ProviderScope(child: ParallelVerseApp()));

class ParallelVerseApp extends StatelessWidget {
  const ParallelVerseApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Parallel Verse',
      debugShowCheckedModeBanner: false,
      theme: ThemeData(
        colorScheme: ColorScheme.fromSeed(seedColor: Colors.deepPurple),
        useMaterial3: true,
      ),
      home: const InstancesPage(),
    );
  }
}

class InstalledApp {
  const InstalledApp({required this.packageName, required this.displayName});

  final String packageName;
  final String displayName;

  factory InstalledApp.fromMap(Map<Object?, Object?> map) {
    return InstalledApp(
      packageName: map['packageName']! as String,
      displayName: map['displayName']! as String,
    );
  }
}

class CloneInstance {
  const CloneInstance({
    required this.id,
    required this.packageName,
    required this.displayName,
    required this.state,
  });

  final String id;
  final String packageName;
  final String displayName;
  final String state;

  factory CloneInstance.fromMap(Map<Object?, Object?> map) {
    return CloneInstance(
      id: map['id']! as String,
      packageName: map['packageName']! as String,
      displayName: map['displayName']! as String,
      state: map['state']! as String,
    );
  }
}

abstract class RuntimeClient {
  Future<List<InstalledApp>> listInstalledApps();

  Future<List<CloneInstance>> listInstances();

  Future<CloneInstance> createInstance(InstalledApp app);
}

class AndroidRuntimeClient implements RuntimeClient {
  static const _channel = MethodChannel(
    'com.parallelverse.controller/runtime',
  );

  @override
  Future<List<InstalledApp>> listInstalledApps() async {
    final result = await _channel.invokeListMethod<Object?>(
      'listInstalledApps',
    );
    return (result ?? const <Object?>[])
        .map((item) => InstalledApp.fromMap(item! as Map<Object?, Object?>))
        .toList(growable: false);
  }

  @override
  Future<List<CloneInstance>> listInstances() async {
    final result = await _channel.invokeListMethod<Object?>('listInstances');
    return (result ?? const <Object?>[])
        .map((item) => CloneInstance.fromMap(item! as Map<Object?, Object?>))
        .toList(growable: false);
  }

  @override
  Future<CloneInstance> createInstance(InstalledApp app) async {
    final result = await _channel.invokeMapMethod<Object?, Object?>(
      'createInstance',
      <String, String>{
        'packageName': app.packageName,
        'displayName': app.displayName,
      },
    );
    return CloneInstance.fromMap(result!);
  }
}

final runtimeClientProvider = Provider<RuntimeClient>(
  (_) => AndroidRuntimeClient(),
);

final instancesProvider = FutureProvider<List<CloneInstance>>(
  (ref) => ref.watch(runtimeClientProvider).listInstances(),
);

class InstancesPage extends ConsumerWidget {
  const InstancesPage({super.key});

  Future<void> _openAppPicker(BuildContext context, WidgetRef ref) async {
    final client = ref.read(runtimeClientProvider);
    await showModalBottomSheet<void>(
      context: context,
      showDragHandle: true,
      isScrollControlled: true,
      builder: (context) => _InstalledAppPicker(
        loadApps: client.listInstalledApps,
        onSelected: (app) async {
          await client.createInstance(app);
          ref.invalidate(instancesProvider);
        },
      ),
    );
  }

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final instances = ref.watch(instancesProvider);
    return Scaffold(
      appBar: AppBar(title: const Text('Parallel Verse')),
      body: instances.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (error, _) => _LoadError(
          onRetry: () => ref.invalidate(instancesProvider),
        ),
        data: (items) => items.isEmpty
            ? _EmptyState(onAdd: () => _openAppPicker(context, ref))
            : ListView.separated(
                padding: const EdgeInsets.all(16),
                itemCount: items.length,
                separatorBuilder: (_, _) => const SizedBox(height: 8),
                itemBuilder: (context, index) {
                  final instance = items[index];
                  return Card(
                    child: ListTile(
                      leading: const CircleAvatar(child: Icon(Icons.apps)),
                      title: Text(instance.displayName),
                      subtitle: Text(instance.packageName),
                      trailing: Text(instance.state.toLowerCase()),
                    ),
                  );
                },
              ),
      floatingActionButton: instances.asData?.value.isNotEmpty == true
          ? FloatingActionButton.extended(
              onPressed: () => _openAppPicker(context, ref),
              icon: const Icon(Icons.add),
              label: const Text('Add app'),
            )
          : null,
    );
  }
}

class _EmptyState extends StatelessWidget {
  const _EmptyState({required this.onAdd});

  final VoidCallback onAdd;

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(32),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(
              Icons.control_point_duplicate,
              size: 64,
              color: Theme.of(context).colorScheme.primary,
            ),
            const SizedBox(height: 20),
            Text(
              'No cloned apps yet',
              style: Theme.of(context).textTheme.headlineSmall,
            ),
            const SizedBox(height: 8),
            const Text(
              'Choose an installed app to create a separate instance.',
              textAlign: TextAlign.center,
            ),
            const SizedBox(height: 24),
            FilledButton.icon(
              onPressed: onAdd,
              icon: const Icon(Icons.add),
              label: const Text('Choose an app'),
            ),
          ],
        ),
      ),
    );
  }
}

class _LoadError extends StatelessWidget {
  const _LoadError({required this.onRetry});

  final VoidCallback onRetry;

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          const Text('Could not load app instances.'),
          const SizedBox(height: 12),
          OutlinedButton(onPressed: onRetry, child: const Text('Try again')),
        ],
      ),
    );
  }
}

class _InstalledAppPicker extends StatefulWidget {
  const _InstalledAppPicker({
    required this.loadApps,
    required this.onSelected,
  });

  final Future<List<InstalledApp>> Function() loadApps;
  final Future<void> Function(InstalledApp app) onSelected;

  @override
  State<_InstalledAppPicker> createState() => _InstalledAppPickerState();
}

class _InstalledAppPickerState extends State<_InstalledAppPicker> {
  late final Future<List<InstalledApp>> _apps = widget.loadApps();
  String _query = '';
  bool _saving = false;

  Future<void> _select(InstalledApp app) async {
    if (_saving) return;
    setState(() => _saving = true);
    try {
      await widget.onSelected(app);
      if (mounted) Navigator.pop(context);
    } on PlatformException catch (error) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(error.message ?? 'Could not add this app.')),
      );
      setState(() => _saving = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return SafeArea(
      child: SizedBox(
        height: MediaQuery.sizeOf(context).height * 0.78,
        child: Column(
          children: [
            Padding(
              padding: const EdgeInsets.fromLTRB(16, 0, 16, 12),
              child: TextField(
                enabled: !_saving,
                decoration: const InputDecoration(
                  prefixIcon: Icon(Icons.search),
                  hintText: 'Search installed apps',
                  border: OutlineInputBorder(),
                ),
                onChanged: (value) => setState(() => _query = value),
              ),
            ),
            Expanded(
              child: FutureBuilder<List<InstalledApp>>(
                future: _apps,
                builder: (context, snapshot) {
                  if (snapshot.connectionState != ConnectionState.done) {
                    return const Center(child: CircularProgressIndicator());
                  }
                  if (snapshot.hasError) {
                    return const Center(
                      child: Text('Could not read installed apps.'),
                    );
                  }
                  final query = _query.trim().toLowerCase();
                  final apps = (snapshot.data ?? const <InstalledApp>[])
                      .where(
                        (app) =>
                            query.isEmpty ||
                            app.displayName.toLowerCase().contains(query) ||
                            app.packageName.toLowerCase().contains(query),
                      )
                      .toList(growable: false);
                  if (apps.isEmpty) {
                    return const Center(child: Text('No matching apps found.'));
                  }
                  return ListView.builder(
                    itemCount: apps.length,
                    itemBuilder: (context, index) {
                      final app = apps[index];
                      return ListTile(
                        enabled: !_saving,
                        leading: const CircleAvatar(child: Icon(Icons.android)),
                        title: Text(app.displayName),
                        subtitle: Text(app.packageName),
                        onTap: () => _select(app),
                      );
                    },
                  );
                },
              ),
            ),
          ],
        ),
      ),
    );
  }
}
