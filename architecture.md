# Parallel Verse System Architecture

## Purpose and authority

This document defines the intended architecture for the consumer edition of Parallel Verse. It supersedes the earlier Android Enterprise/work-profile proposal. Statements marked **Planned** are requirements, not implementation claims. Android platform behaviour and distribution policy override this document.

## Product decision

The generic-device product uses user-space application virtualization. It is not an Android Enterprise DPC and does not create Work Profiles. OEM/AOSP integration is a separate future edition, not a fallback hidden in this app.

Virtual processes share the host application's Android UID. Process slots provide fault and lifecycle separation only; they are never described as an OS-enforced secure container. Banking, DRM, anti-cheat, integrity-sensitive, and other unsupported applications are excluded by the compatibility policy.

## Component and process model

Flutter/Dart is the controller UI only: onboarding, app catalogue, instance management, and settings. Riverpod owns UI state. Pigeon-generated APIs form the typed Flutter-to-Java control boundary.

Java owns Android-framework interaction, Room metadata, process-slot allocation, AIDL, lifecycle recovery, APK inspection, permissions, services, notifications, and compatibility decisions. A Java `RuntimeService` runs in a dedicated manifest-declared process and owns active runtime sessions. Each virtual slot is a bounded, manifest-declared Java process. Virtual slot processes must never initialize Flutter.

The native C++20 engine is optional and narrowly scoped to native loading, filesystem mapping, and measured low-level work. JNI is versioned and coarse; intents and lifecycle callbacks remain in Java.

```text
Flutter UI process -> Pigeon -> Java controller -> AIDL -> Java runtime service
                                                        -> JNI -> C++ engine
                                                        -> bounded virtual slots
```

## Shared contracts

### Instance identity and ownership

An instance has a stable controller-generated ID, a target package name, and one assigned runtime slot while active. Room is the authoritative metadata store. The app-private filesystem owns data under one directory per instance; Dart local storage may hold UI preferences only. Dart and Java must not write the same database.

### States and recovery

Controller-visible states are `draft`, `installing`, `ready`, `starting`, `running`, `stopping`, `stopped`, `unsupported`, and `error`. State changes are persisted before external work. On recovery, Room operation records plus the runtime service's observed state are authoritative; a saved UI state is not proof that a virtual process is alive.

### Native boundary

The only supported conceptual native operations are `createRuntime`, `startInstance`, `stopInstance`, `mountInstanceStorage`, `getRuntimeStatus`, and `destroyRuntime`. The Java API is versioned. Native code must not download or execute external code and must not own Android component lifecycle.

### Security and distribution

Private instance storage is protected by the host app sandbox and keys are held in Android Keystore. This phase is local-only: it has no login, licensing backend, telemetry upload, remote configuration, or server-side logging. A future backend may distribute signed compatibility rules and licence state, but it must never be required to launch an already-authorized local instance and must never supply DEX or native executable code for execution.

## Initial support boundary

First releases target ARM64, a deliberately narrow Android-version matrix, a bounded concurrent-instance count, and an explicit app compatibility list. Users must legitimately provide or install target applications. Compatibility is a tested matrix, not a universal promise.

## Verification boundaries

- Unit tests cover state transitions, slot allocation, and recovery decisions.
- Instrumentation tests cover service binding, process death, storage separation, and notification/background behaviour for supported apps.
- Physical devices and Firebase Test Lab establish Android/OEM compatibility.
- Perfetto and memory tools measure aggregate device cost.
- Security review covers AIDL permissions, exported components, JNI input, storage, manifest provenance, and update channels.

## Feature blueprints

See [the feature-blueprint manifest](blueprints/README.md).
