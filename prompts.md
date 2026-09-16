Flutter + Java is feasible, provided Flutter is only the control UI—not the virtualization runtime. The production architecture should keep the Flutter engine out of resident virtual-app processes.

Annotation 1 For the consumer product you described, we should proceed with user-space virtualization as the generic-device option, while treating OEM integration as a separate future edition. Work Profile + DPC is no longer part of the consumer architecture.

## Recommended stack

| Layer                    | Selection                                                       | Responsibility                                                           |
| ------------------------ | --------------------------------------------------------------- | ------------------------------------------------------------------------ |
| User interface           | Flutter/Dart                                                    | App catalog, instance management, settings, onboarding                   |
| Flutter state            | Riverpod                                                        | UI state and dependency management                                       |
| Flutter–Android bridge   | Pigeon-generated APIs                                           | Typed calls between Dart and Java                                        |
| Android controller       | Java + Android SDK                                              | Permissions, lifecycle, services, notifications, APK inspection          |
| Internal IPC             | AIDL                                                            | Communication with runtime processes                                     |
| Virtualization framework | Java                                                            | Component, package, intent, service and process routing                  |
| Native compatibility     | C++20 + NDK + CMake                                             | Native loading, filesystem mapping and measured low-level runtime work   |
| Java–native bridge       | JNI                                                             | Small, versioned interface into the C++ engine                           |
| Metadata database        | Room/SQLite                                                     | Instance definitions, compatibility rules, operations and recovery state |
| Instance storage         | App-private filesystem                                          | Separate virtual data directories                                        |
| Key protection           | Android Keystore                                                | Encryption keys and sensitive configuration                              |
| Background work          | Foreground service where justified; WorkManager for maintenance | Runtime ownership and deferred housekeeping                              |
| Backend                  | Java 21 + Spring Boot + PostgreSQL                              | Licensing, compatibility manifests, updates and telemetry                |
| Native crash reporting   | Crashlytics/another NDK-capable reporter                        | Java, Flutter and symbolicated native failures                           |
| CI                       | GitHub Actions + Gradle + CMake + Flutter tooling               | Reproducible builds and automated tests                                  |
| Device testing           | Physical-device fleet plus Firebase Test Lab                    | Android and OEM compatibility matrix                                     |

Flutter officially supports calling Java through platform channels. For a larger interface, generated typed messages are safer than maintaining many string-based `MethodChannel` handlers. [Flutter platform integration](https://docs.flutter.dev/platform-integration/platform-channels)

## Process architecture
```text
Flutter UI process
       |
       | Pigeon/platform API
       v
Java UI plugin
       |
       | AIDL
       v
Java runtime service process
       |
       | JNI
       v
C++ native compatibility engine
       |
       +---- virtual process slot 1
       +---- virtual process slot 2
       +---- virtual process slot N
```

The important design choice is process separation:

- The Flutter process displays and configures instances.
- A Java runtime service owns the active sessions.
- The Flutter process can be reclaimed after launching an instance.
- Virtual processes must not initialize Flutter.
- Use a bounded pool of manifest-declared runtime process slots.
- Store each instance under a separate directory and database namespace.

These processes still share your host application’s Android UID. They provide fault and lifecycle separation, but not OS-enforced security isolation. Do not advertise them as secure containers.

## Java versus C++

Java should own anything that communicates with Android framework services:

- Activities, services and receivers
- Binder and intent orchestration
- Notification integration
- Permissions
- Package metadata
- Process-slot allocation
- Runtime recovery
- Compatibility decisions

C++ should be limited to capabilities that actually require native operation or demonstrate a measured advantage. Google’s JNI guidance recommends minimizing both the JNI surface and cross-boundary marshalling. [Android JNI guidance](https://developer.android.com/ndk/guides/jni-tips)

Define a small native API such as:
```text
createRuntime(configuration)
startInstance(instanceId, packageDescriptor)
stopInstance(instanceId)
mountInstanceStorage(instanceId)
getRuntimeStatus(instanceId)
destroyRuntime(instanceId)
```

Do not send every intent or lifecycle callback through JNI. Keep high-frequency Android framework behavior in Java.

## Storage decision

Use Room as the authoritative metadata store because the Java runtime—not Flutter—must continue operating when the UI process is gone.

Suggested ownership:

- Room: instances, process slots, installed-package metadata, compatibility status
- Private filesystem: per-instance application data
- Keystore: master encryption keys
- Flutter local storage: UI preferences only
- Backend: compatibility manifests and license state; never required to launch an already authorized local instance

Avoid having Dart and Java independently write the same SQLite database.

## Backend decision

The runtime should work offline. A backend is useful only for:

- License/subscription validation
- Signed compatibility-rule distribution
- Crash and device compatibility aggregation
- Remote feature flags
- Release/update metadata
- Customer support diagnostics with explicit consent

Start with one Spring Boot service and PostgreSQL. Do not introduce microservices, Kubernetes, Kafka or Redis until measured load requires them.

Compatibility manifests must be signed so a compromised network cannot push arbitrary runtime behavior. Do not remotely download DEX or native executable code; Google Play restricts executable-code downloads and unauthorized interference with other apps. [Google Play policy](https://support.google.com/googleplay/android-developer/answer/16559646)

## Initial support boundary

For a viable first release:

- Support ARM64 devices first.
- Select a narrow Android-version range and expand through testing.
- Limit the number of simultaneously running instances.
- Publish an explicit per-app compatibility list.
- Exclude banking, DRM, anti-cheat and integrity-sensitive applications initially.
- Require the user to provide or legitimately install the target application.
- Test notifications and background behavior independently for every supported app.
- Obtain a Play policy review before making Play Store distribution the business dependency.

This stack can produce a production-quality virtualization product, but it cannot guarantee that every Android application will run. “Production-level” should mean a tested compatibility matrix, controlled failure behavior, secure updates, crash recovery and responsive maintenance—not universal compatibility.





---





start building it iteratively










Add comments on top of the workflow file - about what the conflicts we have faced briefly


use up-to-date checkout with stable commit hash (to prevent chain attacks) in Github Actions workflows


for now skip the signing with keystore, first we build the app and test it and then go for scaling

--------


It didn't cover one perspective .i.e. Tech Stach — why we chose that particular stack for this project




# BLUEPRINT

A "blueprint" is a design document for a core module/component in an system application.
blueprint refers to independently routable work units or areas of concern that can be assigned, delegated, and documented separately.


Create *BLUEPRINT* only if all three are true:
1. **Hard to reverse**
2. **Non-Obvious Logic**
3. **The result of a real trade-off**: there were genuine alternatives and you picked one for specific reasons

If a decision is easy to reverse, skip it: you'll just reverse it. If it's not surprising, nobody will wonder why. If there was no real alternative, there's nothing to record beyond "we did the obvious thing."




Example: Choosing which core database to use, requires a *BLUEPRINT* because migrating data later is a massive project and time consuming. Choosing the color of a button does not, because you can change it in five seconds.


If a new developer (or even you, a year from now) looks at the code and thinks, "This is a really weird way to do this, why didn't they just do X?", then you need a blueprint to provide the missing context. If your solution perfectly follows standard industry practices, it speaks for itself and doesn't need a document.


If there was only one logical way to build the feature, writing a document just to say "we did the obvious thing" is a waste of time.



What problem does the application solve?
What invariants must always remain true?
What happens in difficult edge cases?




# An ADR 
 It only preserves a decision after the team has understood the underlying problem and evaluated the trade-offs.


The application needs immediate UI feedback, but backend confirmation is asynchronous.

