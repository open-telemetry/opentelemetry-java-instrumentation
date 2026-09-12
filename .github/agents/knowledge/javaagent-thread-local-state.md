# [Javaagent] Temporary ThreadLocal State

## Quick Reference

- Use when: designing, implementing, or reviewing temporary `ThreadLocal` state in javaagent advice
  or helpers
- Default: remove the value after use; restore a previous value only for a real stack-like scope

## Choose Cleanup from the Lifecycle

Trace every writer, reader, and cleanup point. Check whether recursion, constructor chaining,
overlapping advice, or callbacks can write a new value while an outer value is still active. Thread
confinement does not prevent this kind of nesting.

| Lifecycle | Cleanup |
| --------- | ------- |
| An inner operation can replace active outer state, and the outer operation needs its state again | Restore the previous value, or remove if none existed |
| A one-shot handoff, non-nestable current-operation marker, or non-overlapping suppression flag | Remove the value |

A boolean does not decide the policy. A nestable suppression guard may need restoration. If no
supported call path needs the outer value again, use `ThreadLocal.remove()` and avoid a scope object
or previous-value field.

Ensure cleanup runs on normal and exceptional exits.
