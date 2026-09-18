# Parallel Verse System Architecture

## Purpose and authority

This document defines the intended architecture for the consumer edition of Parallel Verse. It supersedes the earlier Android Enterprise/work-profile proposal. Statements marked **Planned** are requirements, not implementation claims. Inspected code and tests are authoritative for current behavior; Android platform behavior and distribution policy override this document.

## Product decision

The generic-device product uses user-space application virtualization. It is not an Android Enterprise DPC and does not create Work Profiles. OEM/AOSP integration is a separate future edition, not a fallback hidden in this app.

Virtual processes share the host application's Android UID. Process slots provide fault and lifecycle separation only; they are never described as an OS-enforced secure container. Banking, DRM, anti-cheat, integrity-sensitive, and other unsupported applications are excluded by the compatibility policy.

## Component and process model

Flutter/Dart is the controller UI only: onboarding, app catalog, instance management, and settings. Riverpod owns UI state. Pigeon-generated APIs form the typed Flutter-to-Java control boundary.

Java is the control plane. It owns Android-framework interaction, Room metadata, process-slot allocation, AIDL, lifecycle recovery, APK inspection, permissions, services, notifications, and compatibility decisions. A Java `RuntimeService` runs in a dedicated manifest-declared process and owns active runtime sessions. Each virtual slot is a bounded, manifest-declared Java process. Virtual slot processes must never initialize Flutter.

Rust is the memory-safe native runtime core for storage containment, runtime-state invariants, package/binary inspection, and later measured low-level compatibility work. Rust does not own Android component lifecycle, Binder orchestration, resources, class loading, DEX loading, or policy decisions. Java reaches the core through a coarse, versioned JNI ABI implemented by Rust with C-compatible `extern "system"` exports; high-frequency Android framework behavior remains in Java.

```text
Flutter UI process -> Pigeon -> Java controller -> AIDL -> Java runtime service
                                                        -> Java virtual slot process
                                                           -> versioned JNI ABI -> Rust RuntimeCore
```

The earlier C++ lifecycle tracker is superseded by the Rust RuntimeCore migration. Until the Rust artifact is built, packaged, and exercised by CI, that migration is **Partial** rather than an implemented virtualization engine.

## Shared contracts

### Instance identity and ownership

An instance has a stable controller-generated ID, a target package name, and one assigned runtime slot while active. Room is the authoritative metadata store. The app-private filesystem owns data under one directory per instance; Dart local storage may hold UI preferences only. Dart and Java must not write the same database.

Java imports an immutable execution snapshot for each instance before it becomes `ready`. The snapshot consists of the installed package's base APK and split APKs under the instance directory plus Room-owned package name, version, launcher component, declared activity/service/receiver/provider names, and signing-certificate digest. Runtime code consumes only paths resolved beneath that instance directory. Importing a snapshot is not itself successful clone execution and does not authorize falling back to the device-installed launcher.

A logical virtual-user identity is an application-level namespace associated with instance metadata and storage. It is not an Android UID, does not change the Linux credentials of a process, and is not a security boundary. The Java control plane owns the mapping from controller identity to logical virtual user; the native core may validate and enforce the supplied storage namespace but must not invent identity.

### States and recovery

Controller-visible states are `draft`, `installing`, `ready`, `starting`, `running`, `stopping`, `stopped`, `unsupported`, and `error`. State changes are persisted before external work. On recovery, Room operation records plus the runtime service's observed state are authoritative; a saved UI state is not proof that a virtual process is alive.

Native runtime state is process-local evidence, not durable controller truth. A Rust handle is owned by exactly one Java virtual-slot process, becomes invalid after destruction or process death, and must not be reused across instances or processes. Native failures must be surfaced to Java without independently advancing a persisted instance state. Startup that cannot establish storage containment fails closed.

### Native runtime boundary

The supported native operations are `getApiVersion`, `createRuntime`, `mountInstanceStorage`, `startInstance`, `stopInstance`, `getRuntimeStatus`, and `destroyRuntime`. The Java JNI class and Rust JNI exports are one versioned ABI; there is no separate public C SDK. Java must reject a loaded library whose reported API version differs from its expected version. Calls use opaque handles and bounded strings or byte buffers; ownership and lifetime remain explicit at the boundary. Panics, invalid handles, malformed identifiers, path escapes, and incompatible API versions must not unwind across JNI or produce undefined behavior.

The core accepts only app-private storage roots selected by Java. An instance storage path must resolve beneath its runtime root and cannot be rebound while running. `startInstance` currently means transition of the native bookkeeping state only. It must not be documented as target-APK loading, component virtualization, or successful clone execution until those behaviors exist and have compatibility tests.

### Security and distribution

Private instance storage is protected by the host app sandbox and keys are held in Android Keystore. Logical virtual users and separate directories do not add OS-enforced isolation. Native code must not download or execute external code, weaken Android security flags, or own Android component lifecycle.

This phase is local-only: it has no login, licensing backend, telemetry upload, remote configuration, or server-side logging. A future backend may distribute signed compatibility rules and license state, but it must never be required to launch an already-authorized local instance and must never supply DEX or native executable code for execution.

## Initial support boundary

First releases target ARM64, a deliberately narrow Android-version matrix, a bounded concurrent-instance count, and an explicit app compatibility list. Users must legitimately provide or install target applications. Compatibility is a tested matrix, not a universal promise.

## Verification boundaries

- Rust unit tests cover handle validity, state transitions, identifier validation, and storage-root containment without requiring Android.
- Java unit tests cover controller state transitions and slot allocation.
- Android instrumentation tests cover JNI loading, ABI/version agreement, service binding, process death, storage separation, and notification/background behavior for supported apps.
- CI is the build and test authority for Gradle, Android packaging, and the Rust-to-Android artifact; Gradle is not run locally for this repository.
- Physical devices and Firebase Test Lab establish Android/OEM compatibility.
- Perfetto and memory tools measure aggregate device cost.
- Security review covers AIDL permissions, exported components, JNI inputs, ABI ownership, storage, manifest provenance, and update channels.

## Feature blueprints

See [the blueprint manifest](blueprints/README.md).
