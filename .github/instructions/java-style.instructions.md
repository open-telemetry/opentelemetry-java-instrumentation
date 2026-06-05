---
applyTo: "**/*.java"
---

# Java Style Rules (first-pass review)

Follow `docs/contributing/style-guide.md`.

- **Visibility**: principle of least access. Use the most restrictive modifier
  that still works. Static fields should be `private` unless they are
  constant-like with a `SCREAMING_SNAKE_CASE` name.
- **`final` on classes**: declare public API classes `final` where possible. Do
  **not** add `final` in `javaagent/src/main/`, in `.internal` packages, or in
  test code (paths under `src/test/` or modules whose name starts/ends with
  `testing` or `tests`).
- **`final` on parameters and local variables**: never declare them `final`.
- **Null comparisons**: use `value == null` / `value != null`, not
  `null == value` / `null != value`. Applies to Java, Kotlin, and Scala.
- **`equals` operand order**: prefer `value.equals(CONSTANT)` over
  `CONSTANT.equals(value)`. Do not flip operand order solely as a defensive
  null-safety cleanup; only flip when `value` can actually be null.
- **Class organization**: static fields → static initializer → instance fields
  → constructors → methods → nested classes. Place calling methods above the
  methods they call.
- **Static factory entry points**: place them below fields and immediately
  above constructors — treat factories and constructors as one construction
  section.
- **Static utility classes**: place the private no-arg constructor after all
  methods.
- **Uppercase field names**: use `SCREAMING_SNAKE_CASE` only for constant-like
  values — literals, immutable value constants (e.g. `Duration` timeouts),
  semantic keys/handles (`AttributeKey`, `ContextKey`, `VirtualField`,
  `MethodHandle`, `Pattern`), and canonical singletons (`INSTANCE`, `EMPTY`,
  `NOOP`). Use lower camel case for runtime collaborators (loggers,
  instrumenters, helpers, caches), even when `static final`.
- **Avoid throwaway forwarding locals** that mirror an existing constant,
  argument, or SDK field into both an SDK call and span attributes; pass the
  original value directly unless real derivation justifies a local.
- **`Optional`**: do not use in public API signatures or on the hot path.
- **Semconv constants**: in `library/src/main/`, copy incubating semconv
  constants locally as `private static final` with a `// copied from <Class>`
  comment; do not depend on the semconv incubating artifact. In
  `javaagent/src/main/` and tests, use semconv constants directly.
- **`@Nullable` in tests**: do not add it to test code.
