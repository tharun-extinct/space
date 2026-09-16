---
name: responsibility-blueprints
description: Establish and maintain responsibility-based technical blueprints for capabilities, domains, components, integrations, and workflows. Use when creating or restructuring architecture.md and blueprints/ so agents can safely route change work without treating every blueprint as a feature.
---

# Responsibility Blueprints

Maintain a progressively disclosed technical documentation system. A blueprint is
the focused contract for an **independently routable responsibility**. It is not
limited to a user-facing feature.

Use verified code and tests as evidence of current behaviour. Use explicit
requirements as evidence of intended behaviour. Label unknown, planned, and
conflicting information; do not invent project facts.

## Choose the right artifact

Use `architecture.md` for a reusable contract when either condition is true:

- two or more responsibilities must obey the rule; or
- changing the rule could affect two or more responsibilities.

Examples include system boundaries, data ownership, shared representations,
security, error semantics, recovery, concurrency, and cross-cutting workflows.
Define each shared invariant once under a stable heading. Do not duplicate it in
a blueprint.

Create one BLUEPRINT for each independently routable responsibility. It can be a:

- **capability** — a user or business outcome, such as checkout or export;
- **domain** — a business area with its own rules, vocabulary, lifecycle, or
  ownership, such as billing or scheduling;
- **component** — an internal module with a stable responsibility, such as a
  search index or rules engine;
- **integration** — an external system or protocol with distinct mappings,
  failure modes, and recovery rules; or
- **workflow** — a coherent cross-domain process, such as reconciliation or
  account closure.

Do not create a blueprint merely because a folder, service, class, or ticket
exists.

## Creation decision

Create a new blueprint only when all three tests pass:

1. **Coherence** — the subject has one meaningful responsibility or ownership
   boundary.
2. **Independent change surface** — it has distinct rules, lifecycle,
   acceptance evidence, implementation or test areas, dependencies, or
   operational risks.
3. **Routing value** — a future task can work on it with this focused context
   without also needing unrelated responsibility documents.

If the subject is part of the same responsibility, update the existing blueprint. If it is only a shared rule, update `architecture.md`. If the evidence is insufficient to establish a responsibility, record the uncertainty in the manifest or an existing blueprint rather than creating speculative documents.

## Bootstrap a new project

Once the repository has a declared purpose, a requirement, a bounded problem,
or an initial implementation scaffold, create at least one initial blueprint.
Choose the narrowest truthful starting responsibility:

- `product-core.md` for a small product with one coherent purpose;
- `<primary-domain>.md` for a domain-led system;
- `<primary-capability>.md` for an outcome-led product;
- `system-foundation.md` for a platform or internal technical system.

Mark unimplemented behaviour `Planned` or `Unknown`. The absence of completed code is not a reason to omit a blueprint when the intended responsibility is known.

An empty repository with no known purpose, requirement, or scaffold is the
only normal case where no responsibility blueprint is required. If a manifest
is created in that state, say that project scope is unknown instead of
inventing one.

## Documentation layout and routing

Use these paths unless coherent repository conventions already exist:

```text
architecture.md
blueprints/
  README.md
  <responsibility>.md
```

Use `blueprints/README.md` as the routing manifest. For every blueprint, list:

- task concepts and synonyms;
- blueprint type and primary document;
- exact applicable `architecture.md` headings;
- required and impact-check blueprints;
- principal implementation and test areas.

Route by task intent, not filename alone. For ordinary work, load the primary
blueprint and only its relevant shared-contract sections. For a shared-contract
change, inspect every manifest consumer. Inspect code and tests before
expanding context when routing is uncertain.

## Blueprint contents

Each blueprint must be useful after its declared dependencies are loaded and
include:

1. `Outcome or responsibility`
2. `Current verified status`
3. `Architecture dependencies`
4. `Local rules and implications`
5. `Related blueprints`
6. `Relevant implementation and tests`
7. `Acceptance or verification criteria`
8. `Remaining gaps and unknowns`

Link shared contracts to exact `architecture.md` headings and explain only the
local consequence. A blueprint must not silently redefine a shared invariant.

## Change propagation and validation

For a responsibility-local change, update its blueprint, inspect its linked
shared contracts and impact-check blueprints, then update manifest routing if
the task concepts, dependencies, or code/test areas changed.

For a shared-contract change, update `architecture.md` first, find all manifest
consumers, and check their implications, evidence, implementation, and tests
for drift.

Before finishing, verify local links and anchors, manifest coverage, one source
of truth for shared invariants, and consistency of status claims with inspected
code and tests.

## Guardrails

- Do not turn `architecture.md` into a set of feature specifications.
- Do not force a component-per-blueprint or feature-per-blueprint structure.
- Do not create empty documents solely to satisfy a count.
- Do not hide important routing knowledge in conversational context.
- Do not claim implementation is complete based only on documentation.
