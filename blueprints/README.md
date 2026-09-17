# Blueprint manifest

## Loading protocol

1. Match task intent and synonyms.
2. Load one primary blueprint and only its required architecture anchors.
3. Inspect listed implementation and tests.
4. Load an impact-check blueprint only if its stated trigger applies.
5. For a shared-contract or ambiguous change, load the complete architecture and every manifest entry linking the affected anchor.
6. Do not load unrelated blueprints.

## Responsibility router

| Concepts and synonyms | Responsibility type | Primary blueprint | Required architecture contracts | Required blueprints | Impact checks | Principal implementation and tests |
|---|---|---|---|---|---|---|
| instances, user-space virtualization, clone lifecycle, virtual users, slots, AIDL, Room, recovery | Product runtime orchestration | [User-space runtime](user-space-runtime.md) | [Product decision](../architecture.md#product-decision), [Component and process model](../architecture.md#component-and-process-model), [Instance identity and ownership](../architecture.md#instance-identity-and-ownership), [States and recovery](../architecture.md#states-and-recovery) | None | [Native runtime core](native-runtime-core.md) when native state, storage containment, JNI, or ABI behavior changes | `android/src/main/java/com/parallelspace/controller/runtime`; `android/src/main/aidl`; `android/src/test`; `flutter/lib`; `flutter/test` |
| Rust RuntimeCore, native runtime, JNI ABI, extern system, opaque handles, storage containment, native state | Native compatibility boundary | [Native runtime core](native-runtime-core.md) | [Component and process model](../architecture.md#component-and-process-model), [Instance identity and ownership](../architecture.md#instance-identity-and-ownership), [States and recovery](../architecture.md#states-and-recovery), [Native runtime boundary](../architecture.md#native-runtime-boundary), [Security and distribution](../architecture.md#security-and-distribution), [Verification boundaries](../architecture.md#verification-boundaries) | [User-space runtime](user-space-runtime.md) | [User-space runtime](user-space-runtime.md) when Java lifecycle, AIDL, persisted state, slot ownership, or instance identity changes | `android/runtime-core`; `android/src/main/java/com/parallelspace/controller/runtime/NativeRuntime.java`; `android/src/main/java/com/parallelspace/controller/runtime/VirtualSlotService.java`; `android/build.gradle`; `.github/workflows/verify.yml`; `.github/workflows/release-apk.yml` |

Every non-manifest blueprint in this directory appears exactly once as a primary blueprint above.

## Status vocabulary

- `Implemented` — current behavior is verified by identified code or tests.
- `Partial` — some criteria are verified and the gaps are named.
- `Planned` — intended behavior is not yet verified in code.
- `Unknown` — evidence is insufficient or conflicting.
- `Deprecated` — retained only for compatibility or migration.
- `Superseded` — replaced by a linked blueprint.
