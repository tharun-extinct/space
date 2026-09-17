# Native runtime core

## Outcome or responsibility

Provide a memory-safe Rust RuntimeCore behind a small, versioned JNI ABI implemented with C-compatible `extern "system"` exports for native runtime state and app-private storage containment, while leaving Android framework lifecycle and policy in Java.

This is a distinct responsibility because it has its own ABI and ownership rules, non-Android unit-test surface, build artifact, failure modes, and security review boundary.

## Current verified status

**Partial (verified 2026-09-18).** A Rust crate under `android/runtime-core` and CI/build integration are being introduced concurrently. The existing Java `NativeRuntime` contract already exposes six coarse operations and `VirtualSlotService` owns its handle in one slot process. Until CI builds and packages the Rust library and tests exercise the ABI, only the Java-facing contract and slot ownership are verified. RuntimeCore v1 provides state and storage invariants only; it does not install, load, virtualize, or execute target APKs.

## Architecture dependencies

- [Component and process model](../architecture.md#component-and-process-model) — the core is subordinate to a Java slot process and cannot own Android component lifecycle.
- [Instance identity and ownership](../architecture.md#instance-identity-and-ownership) — Java supplies stable instance/logical-user identity; Rust validates its storage namespace.
- [States and recovery](../architecture.md#states-and-recovery) — native state is process-local evidence and never replaces Room recovery state.
- [Native runtime boundary](../architecture.md#native-runtime-boundary) — JNI/C ABI operations, handle ownership, containment, and failure rules govern the crate.
- [Security and distribution](../architecture.md#security-and-distribution) — the core stays inside the host sandbox and cannot download executable code or claim stronger isolation.
- [Verification boundaries](../architecture.md#verification-boundaries) — Rust unit tests, Android ABI tests, and CI packaging provide different required evidence.

## Local rules and implications

- RuntimeCore exposes the conceptual operations `createRuntime`, `mountInstanceStorage`, `startInstance`, `stopInstance`, `getRuntimeStatus`, and `destroyRuntime`; its Rust JNI exports must preserve their versioned semantics. RuntimeCore v1 does not expose a separate public C SDK.
- Every runtime is represented outside Rust by an opaque handle. Unknown, destroyed, cross-process, and double-destroyed handles fail safely.
- Identifiers are bounded and validated. Storage paths are canonicalized or equivalently normalized, must remain beneath the Java-selected runtime root, and cannot be rebound while an instance is running.
- State transitions are deterministic and synchronized. Rust reports transition errors to Java and never independently persists controller state.
- Rust panics and allocation/encoding failures cannot unwind through the C ABI or JNI.
- The Android artifact targets ARM64 first. Build scripts must not fetch or execute arbitrary runtime code after application installation.
- A successful native `startInstance` means native bookkeeping reached `running`; it is not evidence of APK execution.

## Related blueprints

### Required

- [User-space runtime](user-space-runtime.md) — supplies Java lifecycle, instance identity, slot ownership, and durable-state context required to interpret native operations.

### Impact checks

- [User-space runtime](user-space-runtime.md) — required when changing Java lifecycle, AIDL, persisted state, slot ownership, instance identity, or user-visible execution status.

## Relevant implementation and tests

- `android/runtime-core/Cargo.toml` and `android/runtime-core/src/lib.rs` — Rust crate, JNI exports, runtime registry, state invariants, containment checks, and unit tests (concurrent implementation; verify before relying on it).
- `android/src/main/java/com/parallelspace/controller/runtime/NativeRuntime.java` — versioned Java JNI surface.
- `android/src/main/java/com/parallelspace/controller/runtime/VirtualSlotService.java` — one-process handle owner and caller.
- `android/build.gradle` — Android native artifact packaging integration.
- `.github/workflows/verify.yml` — authoritative Rust tests and Android debug build.
- `.github/workflows/release-apk.yml` — authoritative release-path Rust build and APK packaging.

## Acceptance or verification criteria

- [ ] CI runs formatting/linting and Rust unit tests for RuntimeCore v1.
- [ ] CI cross-compiles the ARM64 shared library and packages it into the Android APK.
- [ ] Android verifies that `NativeRuntime.API_VERSION` matches the loaded native ABI.
- [ ] Invalid and destroyed handles fail without crashes, leaks, or undefined behavior.
- [ ] Traversal, sibling-prefix, absolute-path, symlink, and rebind attempts cannot escape or replace an instance storage root.
- [ ] Legal state transitions and rejected transitions have deterministic unit coverage.
- [ ] Panics are contained before the JNI/C ABI boundary and surfaced as stable errors.
- [ ] Java integration tests distinguish native bookkeeping success from actual target-app execution.

## Remaining gaps and unknowns

- The concurrent Rust implementation and CI integration have not yet been verified by a completed workflow run.
- JNI symbol/loading and ABI-version agreement still need Android instrumentation coverage.
- Filesystem mapping beyond root containment, package parsing, native-library inspection/loading, and measured compatibility hooks are planned, not current behavior.
- APK installation, DEX/resources/class loading, component routing, Binder adaptation, and Android-version compatibility remain Java/runtime-wide future work, not RuntimeCore v1.
