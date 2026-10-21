---
applyTo: "**/*.java,**/*.kt,**/*.scala,**/*.groovy"
---

# Emitted telemetry

Apply these checks when changed production code emits or modifies telemetry,
including instrumentation, SDK support, agent support, and extension modules.
Comment only on a changed line and only for a supported issue.

- For a change implementing semantic conventions, establish the applicable released convention
  and effective mode from the module's telemetry contract. The semconv dependency version is
  not proof that the instrumentation adopted its latest rules. If the target cannot be
  established, do not report a version-specific violation.
- Compare the whole applicable convention for the signal, role, and operation: emission
  boundary, names, kinds, status, metric units, attributes and value rules, omissions, error
  classification, privacy, and cardinality. Check specific domain rules and footnotes before
  general guidance. A key's presence in the attribute registry alone does not establish
  applicability.
- Evaluate inclusion level separately from value rules. Report a feasible, unexplained
  departure from `SHOULD` or `RECOMMENDED` as a departure from guidance, not a `MUST`
  violation. Accept justified constraints and permitted `MAY` choices. Reuse a semconv
  constant only when both key name and type match; in library production code copy
  incubating constants locally rather than importing them. Tests may import both
  stable and incubating constants directly.

## Database and RPC response status codes

This repository intentionally omits `db.response.status_code` and `rpc.response.status_code` when an instrumentation obtains status codes only from failures and the attribute would merely duplicate the code recorded in `error.type`. For databases, this is a repository policy despite the semantic conventions' conditional inclusion requirement, not an upstream exception. Do not report this intentional omission as a conformance defect or request adding the redundant attribute.

Treat this as an instrumentation-level distinction, not per-call deduplication. When an instrumentation captures statuses for both successful and failed operations, retain the existing status attributes on both, including failure statuses that match `error.type`. Do not request removing this existing capture or fabricating successful statuses to justify adding a separate response-status attribute. Preserve status-based error classification and exception-type fallback.

See <a href="https://github.com/open-telemetry/semantic-conventions/issues/3478">open-telemetry/semantic-conventions#3478</a> and the <a href="https://github.com/open-telemetry/opentelemetry-java-instrumentation/pull/16276#discussion_r2850538387">original discussion</a> for rationale. This is a repository policy, not an upstream semantic-convention exception.
