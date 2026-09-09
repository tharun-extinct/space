import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:parallel_verse_controller/main.dart';

class FakeRuntimeClient implements RuntimeClient {
  @override
  Future<CloneInstance> createInstance(InstalledApp app) {
    throw UnimplementedError();
  }

  @override
  Future<List<InstalledApp>> listInstalledApps() async => const [];

  @override
  Future<List<CloneInstance>> listInstances() async => const [];
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
}
