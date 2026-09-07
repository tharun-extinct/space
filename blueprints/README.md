# Feature blueprint manifest

Load one primary blueprint and only the linked architecture sections for an ordinary feature task. Inspect linked impact-check blueprints only when the change can affect them. For a shared architecture change, load all of `architecture.md` and every listed consumer. Inspect code and tests before expanding context when routing is uncertain.

| Concepts | Primary blueprint | Architecture contracts | Implementation and tests |
| --- | --- | --- | --- |
| instances, runtime, virtualisation, slots, AIDL, JNI, storage | [User-space runtime](user-space-runtime.md) | Product decision; Component and process model; Shared contracts | `apps/android`, `apps/flutter`, `tests` |

All blueprints in this directory must appear in this table.
