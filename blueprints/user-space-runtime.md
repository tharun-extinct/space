# User-space runtime

## Outcome or responsibility

A Flutter controller creates and observes instance records through typed Android APIs while the Java control plane owns session lifecycle, logical virtual-user mapping, runtime slots, and recovery without initializing Flutter in resident runtime processes.

## Current verified status

**Partial (verified 2026-09-18).** The repository contains a Flutter launcher hosted by `FlutterActivity`, a Java control plane with Room-backed instance and virtual-package records, immutable base/split APK import, launcher/version/signer metadata, a declared-component catalog, bounded slot allocation, AIDL runtime control, and two manifest-declared Java-only slot services. The UI displays persisted instance state and disables launch for failed imports. Java unit tests cover controller transitions, slot allocation, and imported-path containment. A slot can validate the imported classpath and resolve the launcher class without initialization before entering native bookkeeping state, but it does not instantiate or attach the target APK's Android components. GitHub Actions is the declared Gradle/build authority; this documentation review did not execute Gradle.

## Architecture dependencies

- [Product decision](../architecture.md#product-decision) — Work Profile and DPC APIs are outside the consumer product.
- [Component and process model](../architecture.md#component-and-process-model) — Flutter stays in the UI process; Java owns Android framework and runtime-process lifecycle.
- [Instance identity and ownership](../architecture.md#instance-identity-and-ownership) — Room identity and Java-owned logical-user mapping govern per-instance storage and slots.
- [States and recovery](../architecture.md#states-and-recovery) — persisted Java state remains authoritative over native or UI observations.

## Local rules and implications

- An instance ID and logical virtual-user identity are application namespaces, not Android UIDs or security containers.
- Only the Java controller may import APK snapshots, record signer and launcher provenance, allocate slots, persist lifecycle transitions, and decide whether an installed package is eligible.
- Runtime and slot services remain non-exported, bounded, and Flutter-free.
- A slot confirms contained APK/Dex preparation and native bookkeeping before the coordinator advances `starting` to `running`; failure advances the record to `error`. Even `running` remains runtime-slot state and is not a claim that the guest launcher Activity is visible.
- This phase has no backend dependency: login, licensing, remote manifests, telemetry, and server-side logging remain deferred.

## Incremental delivery plan

1. **RuntimeCore v1 — Partial:** build and package the Rust state/storage core, enforce its JNI API version, and verify containment and transition tests in CI.
2. **Virtual package catalog — Partial:** base and split APKs are copied into instance-owned storage and Room persists signer digest, package/version metadata, launcher component, and declared component names. Intent filters, compatibility rules, re-import after source updates, cleanup, and physical-device verification remain.
3. **Logical virtual users — Partial:** complete crash-recoverable `instanceId` to storage namespace and slot mapping, with Android tests for process death and storage separation.
4. **Component routing — Planned:** add manifest-declared Java stubs and route activities, services, receivers, and providers only for an allowlisted compatibility target.
5. **Guest loading — Partial:** the slot builds a `DexClassLoader` from the contained base/split snapshot and resolves the recorded launcher class without initializing it. Resource creation, native-library loading, `Application` creation, and component attachment remain planned for a controlled test APK family.
6. **Compatibility expansion — Planned:** expand Android/OEM/app coverage only from physical-device evidence and retain controlled failure for protected or unsupported apps.

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
- `android/src/test/java/com/parallelspace/controller/runtime` — transition, slot-allocation, and imported-path-containment unit tests.
- `.github/workflows/verify.yml` and `.github/workflows/release-apk.yml` — authoritative CI verification and test-release packaging.

## Acceptance or verification criteria

- [x] Flutter is not initialized by declared runtime processes.
- [x] Runtime control uses an explicit AIDL API and non-exported services.
- [x] Metadata has one authoritative Java-owned Room schema.
- [x] Instance metadata is persisted before runtime slot assignment.
- [x] Slot allocation is bounded and has unit coverage.
- [x] Creation passes through persisted `draft` and `installing` states and imports an instance-owned APK snapshot before `ready`.
- [x] Room records package/version, launcher and declared components, signer digest, and imported APK paths.
- [x] The slot resolves a launcher class from the contained base/split APK classpath without initializing guest code.
- [x] The coordinator advances `starting` only after the slot reports successful package and native preparation.
- [x] CI defines Flutter and Android verification and test-APK publication workflows.
- [ ] Runtime recovery, process death, and storage separation have instrumentation coverage.
- [ ] Java and native statuses are reconciled without treating process-local state as durable truth.
- [ ] A target APK is installed into a virtual namespace and launched through virtualized component routing.
- [ ] An allowlisted target application completes physical-device compatibility validation.

## Remaining gaps and unknowns

- APK snapshot import is implemented but update reconciliation and orphan cleanup are not.
- Launcher-class resolution is implemented, but resources, class initialization, component attachment/routing, PackageManager and ActivityManager virtualization, and native-library loading are not.
- Runtime recovery and per-instance storage isolation still need Android instrumentation tests.
- The Rust RuntimeCore build, JNI loading, and artifact packaging remain partial until CI verifies them.
- Durable release signing, crash reporting, and device-fleet testing are deferred.
