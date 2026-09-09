import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

final instancesProvider = StateProvider<List<String>>((_) => const []);

void main() => runApp(const ProviderScope(child: parallelverseApp()));

class parallelverseApp extends StatelessWidget {
  const parallelverseApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Parallel Verse',
      home: Scaffold(
        appBar: AppBar(title: const Text('Instances')),
        body: const _InstanceList(),
      ),
    );
  }
}

class _InstanceList extends ConsumerWidget {
  const _InstanceList();

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final instances = ref.watch(instancesProvider);
    if (instances.isEmpty) {
      return const Center(child: Text('No instances yet'));
    }
    return ListView(
      children: [
        for (final instance in instances) ListTile(title: Text(instance)),
      ],
    );
  }
}
