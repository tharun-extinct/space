# User-space runtime

## Outcome

A Flutter controller can create and observe instance records through typed Android APIs while the Java runtime owns session state and never starts Flutter in a runtime process.

## Current verified status

**Partial (2026-09-09).** The repository has a Java runtime control plane with durable instance records, installed-package eligibility checks, bounded slot allocation, slot services, a native lifecycle handle, and unit tests for transition/slot decisions. GitHub Actions is configured for Flutter analysis/tests, Android build/tests, and automatic main-branch debug-signed test APK publication. No successful publication workflow run has yet verified the path, and Gradle has not been run locally by design.

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
- `.github/workflows/verify.yml`: authoritative Flutter and Android verification; all third-party actions are pinned to reviewed immutable commit SHAs.
- `.github/workflows/release-apk.yml`: main-branch push and manual test publisher; it creates a unique `test-release-<run>` tag and attaches a debug-signed APK to a GitHub prerelease without repository signing secrets. All third-party actions are pinned to reviewed immutable commit SHAs.

## Acceptance criteria

- [x] Flutter is not initialized by the declared runtime process.
- [x] Runtime control uses an explicit AIDL API.
- [x] Metadata has one authoritative Java-owned Room schema.
- [x] The native API is small and versioned.
- [x] Instance metadata is persisted before runtime slot assignment.
- [x] Slot allocation is bounded and has unit coverage.
- [x] CI defines Flutter and Android verification jobs.
- [x] Every third-party GitHub Action is pinned to an immutable full commit SHA with its stable release version documented inline.
- [x] CI defines a main-only push workflow that tests and builds an installable test APK, creates a unique test tag, retains an Actions artifact, and publishes a GitHub prerelease.
- [ ] The project builds on Android and Flutter toolchains.
- [ ] Runtime recovery and storage isolation have automated tests.
- [ ] An allowlisted target application completes compatibility validation.

## Remaining gaps

- Generate Pigeon Java/Dart bindings using the Flutter toolchain.
- Add the actual virtualization framework only after defining legal and technical compatibility requirements.
- Replace debug signing with durable release signing before production distribution; debug builds from different CI runs may not update one another in place.
- Verify the first test tag and GitHub prerelease publication through GitHub Actions.
- Configure crash reporting and device-fleet testing.
