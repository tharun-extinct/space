# User-space runtime

## Outcome

A Flutter controller can create and observe instance records through typed Android APIs while the Java runtime owns session state and never starts Flutter in a runtime process.

## Current verified status

**Partial (2026-09-08).** The repository has a Java runtime control plane with durable instance records, installed-package eligibility checks, bounded slot allocation, slot services, a native lifecycle handle, and unit tests for transition/slot decisions. GitHub Actions is configured for Flutter analysis/tests, Android build/tests, and manually dispatched release-APK generation. No workflow run has yet verified the changes, and Gradle has not been run locally by design.

## Architecture dependencies

- [Product decision](../architecture.md#product-decision): no Work Profile or DPC APIs are part of this product.
- [Component and process model](../architecture.md#component-and-process-model): Flutter is limited to the UI process; Java owns runtime processes.
- [Instance identity and ownership](../architecture.md#instance-identity-and-ownership): Room and per-instance private directories are authoritative.
- [States and recovery](../architecture.md#states-and-recovery): operations are durable and recovered from persisted runtime state.

## Feature-specific implications

The initial implementation deliberately does not load, modify, or execute a target APK. It provides the safe control-plane seams required before an application compatibility runtime is introduced.

This phase has no backend dependency: login, licensing, remote manifests, telemetry, and server-side logging are explicitly deferred.

## Related blueprints

### Required

None.

### Impact checks

None yet.

## Relevant implementation and tests

- `flutter`: Riverpod UI and Pigeon schema.
- `android`: Java controller, AIDL service, Room repository, slot services, native API.
- `android/src/test`: Java state-transition unit tests.
- `.github/workflows/verify.yml`: authoritative Flutter and Android verification.
- `.github/workflows/release-apk.yml`: manually dispatched APK build; it signs only when all configured repository signing secrets are present.

## Acceptance criteria

- [x] Flutter is not initialized by the declared runtime process.
- [x] Runtime control uses an explicit AIDL API.
- [x] Metadata has one authoritative Java-owned Room schema.
- [x] The native API is small and versioned.
- [x] Instance metadata is persisted before runtime slot assignment.
- [x] Slot allocation is bounded and has unit coverage.
- [x] CI defines Flutter and Android verification jobs.
- [x] CI can build and retain a manually dispatched release APK artifact without running Gradle locally.
- [ ] The project builds on Android and Flutter toolchains.
- [ ] Runtime recovery and storage isolation have automated tests.
- [ ] An allowlisted target application completes compatibility validation.

## Remaining gaps

- Generate Pigeon Java/Dart bindings using the Flutter toolchain.
- Add the actual virtualization framework only after defining legal and technical compatibility requirements.
- Add the four release-signing secrets before distributing a production APK; unsigned artifacts are CI-only outputs.
- Configure crash reporting and device-fleet testing.
