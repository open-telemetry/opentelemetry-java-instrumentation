# PR #20075 major-simplification review

## Source pull request identity

- URL: https://github.com/open-telemetry/opentelemetry-java-instrumentation/pull/20075
- State at verification: OPEN
- Base repository: `open-telemetry/opentelemetry-java-instrumentation`
- Base branch: `trask-redis-redisson-targets`
- Head repository: `open-telemetry/opentelemetry-java-instrumentation`
- Head branch: `trask-redis-rediscala-targets`
- Head SHA: `faba43d96123befb02625b2faa3a7efb7b04d8ae`

The live pull request matched every captured identity field before its diff was inspected.
The GitHub pull request diff was used as the authoritative change set.

## Candidate inventory

### Mutable-pool instrumentation and snapshot state

- **Changed code and complexity:** `RediscalaMutablePoolInstrumentation` plus the
  `MutablePoolState` virtual field, constructor initialization, mutation refresh/error handling,
  reflective connection-map access, synchronization, and immutable snapshot publication.
- **Established facility or exemplar:** `VirtualField` is the repository facility for per-object
  javaagent state. The implementation uses it to publish complete snapshots without retaining pool
  objects externally.
- **Behavior preservation:** Not established for the plausible alternatives. Reading the mutable
  map on every request would move reflection and locking onto the hot path and would not preserve
  the current fail-closed state after a failed mutation. Rebuilding state only from constructor and
  mutation arguments would have to duplicate Rediscala's equality, mutation-success, and
  concurrency semantics.
- **Materiality:** Removing this branch would be material.
- **Disposition:** Rejected. No substantially simpler alternative was shown to preserve mutation
  failures, races, initialization, and exact configured membership.

### Reflective version and fork compatibility

- **Changed code and complexity:** Cached reflective lookup and invocation for cluster, pool,
  master/replica, mutable-pool, and Sentinel accessors.
- **Established facility or exemplar:** The adjacent Redisson configured-target implementations
  also use cached reflection for APIs that vary across supported versions.
- **Behavior preservation:** Direct calls are not equivalent across the supported Scala 2.11,
  2.12, and 2.13 artifacts, and some supported forks omit the master/replica accessors. Removing
  the dispatch would narrow compatibility or weaken fail-closed behavior.
- **Materiality:** Removing reflection would be material.
- **Disposition:** Rejected. The complexity is required by the supported compatibility range.

### Transaction state wrapper

- **Changed code and complexity:** `RediscalaTransactionState` carries both the originating client
  and the transaction-creation endpoint through one `VirtualField`.
- **Established facility or exemplar:** A single typed `VirtualField` value is the established
  mechanism for composite advice state.
- **Behavior preservation:** The two values have different purposes: legacy output retains the
  creation-time endpoint, while stable output resolves the configured target and current endpoint
  at execution time. Keeping only either value, or recomputing both, changes reconnect behavior;
  separate virtual fields increase rather than reduce indirection.
- **Materiality:** Removing the wrapper would be a moderate reduction.
- **Disposition:** Rejected. The wrapper is the smallest clear representation of the two required
  timing semantics.

### Lazy request-target caching

- **Changed code and complexity:** A `VirtualField<Request, RedisServerTarget>` and a small generic
  lazy-population helper cache topology extraction for request-backed clients.
- **Established facility or exemplar:** `VirtualField` is the repository facility for attaching
  cached instrumentation data to third-party instances.
- **Behavior preservation:** Removing the cache would repeat reflection, collection traversal, and
  sorting for every request. Caching mutable pools instead would be incorrect, so they are
  deliberately routed through their separately refreshed state.
- **Materiality:** Inlining or deleting the helper saves little code; deleting the cache has an
  operational cost.
- **Disposition:** Rejected. It is neither a qualifying simplification nor a behavior-neutral
  replacement.

### Shared endpoint conversion

- **Changed code and complexity:** Similar Scala `Iterable` loops convert pool servers,
  master/replica servers, Sentinel tuples, and mutable-map entries into endpoint strings.
- **Established facility or exemplar:** `RedisServerTarget` already supplies the shared ordering,
  logical-name, single-endpoint, and master-first aggregation operations, and this PR uses them.
- **Behavior preservation:** The remaining loops accept different element shapes and deliberately
  differ in invalid-element handling and result construction. A generic mapper would merely move
  those branches behind another abstraction.
- **Materiality:** Only minor local deduplication is available.
- **Disposition:** Rejected. It does not meet the material-simplification threshold.

### Test organization and repeated assertions

- **Changed code and complexity:** The configured-target integration suite repeats some topology
  setup, lifecycle cleanup, and stable/legacy assertions.
- **Established facility or exemplar:** The suite already centralizes its common configured-target
  span checks in `assertConfiguredTargetSpan`.
- **Behavior preservation:** More parameterization would still require topology-specific creation,
  cleanup, version gating, operation selection, and expected targets, while making those cases less
  explicit.
- **Materiality:** Any remaining reduction is minor and test-only.
- **Disposition:** Rejected. No major simplification is available.

## Result

No candidate met both required gates: material reduction in implementation complexity and
established behavior preservation. No production, test, configuration, or documentation code was
changed, and no simplification commit was created.

Because the result is report-only, no formatter, build, or test run was required. The final
generated-history inspection against source head
`faba43d96123befb02625b2faa3a7efb7b04d8ae` found exactly one generated commit: this single-parent
report-only commit, whose only changed path is
`.github/agent-task-reports/d2f48eac-e1af-432e-8aa2-a1af27d3a3a3.md`.
