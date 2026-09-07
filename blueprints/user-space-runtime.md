# User-space runtime

## Outcome

A Flutter controller can create and observe instance records through typed Android APIs while the Java runtime owns session state and never starts Flutter in a runtime process.

## Current verified status

**Partial (2026-09-08).** The repository has a source scaffold for the controller, Android runtime, AIDL contract, Room schema, transition guard, and native boundary. It has not been built on this machine because Flutter and Gradle are absent.

## Architecture dependencies

- [Product decision](../architecture.md#product-decision): no Work Profile or DPC APIs are part of this product.
- [Component and process model](../architecture.md#component-and-process-model): Flutter is limited to the UI process; Java owns runtime processes.
- [Instance identity and ownership](../architecture.md#instance-identity-and-ownership): Room and per-instance private directories are authoritative.
- [States and recovery](../architecture.md#states-and-recovery): operations are durable and recovered from persisted runtime state.

## Feature-specific implications

The initial implementation deliberately does not load, modify, or execute a target APK. It provides the safe control-plane seams required before an application compatibility runtime is introduced.

## Related blueprints

### Required

None.

### Impact checks

None yet.

## Relevant implementation and tests

- `apps/flutter`: Riverpod UI and Pigeon schema.
- `apps/android`: Java controller, AIDL service, Room entity/DAO, native API.
- `android/src/test`: Java state-transition unit tests.

## Acceptance criteria

- [x] Flutter is not initialized by the declared runtime process.
- [x] Runtime control uses an explicit AIDL API.
- [x] Metadata has one authoritative Java-owned Room schema.
- [x] The native API is small and versioned.
- [ ] The project builds on Android and Flutter toolchains.
- [ ] Runtime recovery and storage isolation have automated tests.
- [ ] An allowlisted target application completes compatibility validation.

## Remaining gaps

- Generate Pigeon Java/Dart bindings using the Flutter toolchain.
- Add the actual virtualization framework only after defining legal and technical compatibility requirements.
- Configure signing, CI, crash reporting, and device-fleet testing.
