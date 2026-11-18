---
applyTo: "**/*.java,**/*.kt,**/*.scala"
---

# Java performance

Apply these rules to Java code. The hot-path allocation rules also apply to Kotlin and Scala.

- **Hot-path allocation**: avoid allocations on the hot path whenever possible, including iterators.
  A for-each loop over a plain Java array does not allocate an iterator.
  Non-allocating Stream API usage is acceptable but may not fit the surrounding code.
  Consider the complexity of pre-sizing `collect` sinks and allocations from
  capturing `forEach` lambdas when choosing an implementation.
- **Retained lists**: when retaining an incoming list as long-lived configuration or metadata,
  take a private `ArrayList` snapshot, including for empty inputs, and consistently use that owned
  copy internally. Treat it as read-only after initialization and preserve unmodifiable views when
  exposing lists. Enhanced-for traversal is fine; arrays or indexed loops are not required. A fresh,
  already privately owned `ArrayList` does not need another copy. This provides structural isolation
  from caller mutation and a consistent stored implementation for empty and nonempty inputs.
- **Lambda allocation**: do not hoist a non-capturing lambda or
  method reference into a field to avoid allocation. HotSpot caches it at
  the `invokedynamic` call site.
