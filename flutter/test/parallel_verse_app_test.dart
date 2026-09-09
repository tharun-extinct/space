import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:parallel_verse_controller/main.dart';

class FakeRuntimeClient implements RuntimeClient {
  FakeRuntimeClient({this.instances = const <CloneInstance>[]});

  final List<CloneInstance> instances;
  final List<String> openedInstanceIds = <String>[];

  @override
  Future<CloneInstance> createInstance(InstalledApp app) {
    throw UnimplementedError();
  }

  @override
  Future<List<InstalledApp>> listInstalledApps() async => const [];

  @override
  Future<List<CloneInstance>> listInstances() async => instances;

  @override
  Future<void> openInstance(CloneInstance instance) async {
    openedInstanceIds.add(instance.id);
  }
}

void main() {
  testWidgets(
    'offers app selection when there are no instances',
    (tester) async {
      await tester.pumpWidget(
        ProviderScope(
          overrides: [
            runtimeClientProvider.overrideWithValue(FakeRuntimeClient()),
          ],
          child: const ParallelVerseApp(),
        ),
      );
      await tester.pumpAndSettle();

      expect(find.text('Parallel Verse'), findsOneWidget);
      expect(find.text('No cloned apps yet'), findsOneWidget);
      expect(find.text('Choose an app'), findsOneWidget);
    },
  );

  testWidgets('opens an existing app instance from its card', (tester) async {
    final runtime = FakeRuntimeClient(
      instances: const <CloneInstance>[
        CloneInstance(
          id: 'instance-1',
          packageName: 'com.example.app',
          displayName: 'Example',
          state: 'READY',
        ),
      ],
    );

    await tester.pumpWidget(
      ProviderScope(
        overrides: [runtimeClientProvider.overrideWithValue(runtime)],
        child: const ParallelVerseApp(),
      ),
    );
    await tester.pumpAndSettle();

    await tester.tap(find.text('Example'));
    await tester.pump();

    expect(runtime.openedInstanceIds, <String>['instance-1']);
  });
}
