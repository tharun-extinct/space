# User-space runtime

## Outcome or responsibility

A Flutter controller creates and observes instance records through typed Android APIs while the Java control plane owns session lifecycle, logical virtual-user mapping, runtime slots, and recovery without initializing Flutter in resident runtime processes.

## Current verified status

**Partial (verified 2026-09-18).** The repository contains a Flutter launcher hosted by `FlutterActivity`, a Java control plane with Room-backed instance records, installed-package eligibility checks, bounded slot allocation, AIDL runtime control, and two manifest-declared Java-only slot services. Java unit tests cover controller transitions and slot allocation. The slot service invokes a coarse native lifecycle API, but its current `startInstance` call is bookkeeping only and does not load or virtualize a target APK. GitHub Actions is the declared Gradle/build authority; this documentation review did not execute Gradle.

## Architecture dependencies

- [Product decision](../architecture.md#product-decision) — Work Profile and DPC APIs are outside the consumer product.
- [Component and process model](../architecture.md#component-and-process-model) — Flutter stays in the UI process; Java owns Android framework and runtime-process lifecycle.
- [Instance identity and ownership](../architecture.md#instance-identity-and-ownership) — Room identity and Java-owned logical-user mapping govern per-instance storage and slots.
- [States and recovery](../architecture.md#states-and-recovery) — persisted Java state remains authoritative over native or UI observations.

## Local rules and implications

- An instance ID and logical virtual-user identity are application namespaces, not Android UIDs or security containers.
- Only the Java controller may allocate slots, persist lifecycle transitions, and decide whether an installed package is eligible.
- Runtime and slot services remain non-exported, bounded, and Flutter-free.
- A slot may report native bookkeeping status, but Java must not translate that alone into a claim that a cloned application is executing.
- This phase has no backend dependency: login, licensing, remote manifests, telemetry, and server-side logging remain deferred.

## Related blueprints

### Required

None.

### Impact checks

- [Native runtime core](native-runtime-core.md) — inspect when native state, storage containment, JNI signatures, ABI versioning, or runtime packaging changes.

## Relevant implementation and tests

- `flutter/lib` and `flutter/test` — Riverpod controller UI, Pigeon-facing behavior, and widget coverage.
- `android/src/main/java/com/parallelspace/controller` — Flutter host and Java platform API.
- `android/src/main/java/com/parallelspace/controller/runtime` — service lifecycle, repository, state model, slot allocation, and native adapter.
- `android/src/main/aidl/com/parallelverse/controller/runtime/IRuntimeService.aidl` — internal runtime control surface.
- `android/src/main/AndroidManifest.xml` — non-exported runtime and slot process declarations.
- `android/src/test/java/com/parallelspace/controller/runtime` — transition and slot-allocation unit tests.
- `.github/workflows/verify.yml` and `.github/workflows/release-apk.yml` — authoritative CI verification and test-release packaging.

## Acceptance or verification criteria

- [x] Flutter is not initialized by declared runtime processes.
- [x] Runtime control uses an explicit AIDL API and non-exported services.
- [x] Metadata has one authoritative Java-owned Room schema.
- [x] Instance metadata is persisted before runtime slot assignment.
- [x] Slot allocation is bounded and has unit coverage.
- [x] CI defines Flutter and Android verification and test-APK publication workflows.
- [ ] Runtime recovery, process death, and storage separation have instrumentation coverage.
- [ ] Java and native statuses are reconciled without treating process-local state as durable truth.
- [ ] A target APK is installed into a virtual namespace and launched through virtualized component routing.
- [ ] An allowlisted target application completes physical-device compatibility validation.

## Remaining gaps and unknowns

- Target APK/package installation, component routing, resources/class loading, PackageManager and ActivityManager virtualization, and native-library loading are not implemented.
- Runtime recovery and per-instance storage isolation still need Android instrumentation tests.
- The Rust RuntimeCore build, JNI loading, and artifact packaging remain partial until CI verifies them.
- Durable release signing, crash reporting, and device-fleet testing are deferred.
