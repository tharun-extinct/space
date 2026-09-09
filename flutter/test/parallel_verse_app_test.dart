import 'package:flutter_test/flutter_test.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:parallel_verse_controller/main.dart';

void main() {
  testWidgets('shows the empty instance state', (tester) async {
    await tester.pumpWidget(
      const ProviderScope(child: parallelverseApp()),
    );

    expect(find.text('Instances'), findsOneWidget);
    expect(find.text('No instances yet'), findsOneWidget);
  });
}
