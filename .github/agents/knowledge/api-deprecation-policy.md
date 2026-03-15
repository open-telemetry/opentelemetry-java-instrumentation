# [API] Breaking Changes and Deprecation Policy

## Quick Reference

- Use when: reviewing public API removals/renames, `@Deprecated` usage, stable-vs-alpha compatibility
- Review focus: deprecate-then-remove timing, delegation direction, required Javadoc/CHANGELOG coverage

## When Are Breaking Changes Allowed?

Only in **non-stable (alpha) modules** — i.e. artifacts whose version has the `-alpha` suffix.
Stable module APIs follow strict backwards compatibility:

- Items in stable modules **can be deprecated** in any minor release.
- Deprecated items in stable modules **cannot be removed until the next major version** (currently targeting 3.0).

The CHANGELOG uses distinct headings to distinguish:

- `⚠️ Breaking changes to non-stable APIs` — alpha/non-stable modules (routine)
- `⚠️ Breaking Changes` — stable module changes (rare, requires strong justification)

## The Deprecate-Then-Remove Cycle

### Alpha (non-stable) modules

Deprecations in alpha modules are introduced in one monthly release and removed in a subsequent
one. The gap is typically **one release** (approximately one month).

### Stable modules

Deprecations in stable modules accumulate over multiple releases and are only **removed in the
next major version** (3.0). Many items carry `// to be removed in 3.0` or `@deprecated ... Will
be removed in 3.0` comments to make this explicit.

## Correct `@Deprecated` Usage

```java
/**
 * @deprecated Use {@link #newMethod()} instead. Will be removed in a future release.
 */
@Deprecated // will be removed in X.Y
public ReturnType oldMethod() {
  return newMethod();  // delegate to the replacement
}
```

Rules:

- Use plain `@Deprecated` — do **not** use `forRemoval=true` or `since="..."` (must stay Java 8 compatible).
- Always include a `@deprecated` Javadoc tag that names the replacement and states the removal timeline.
- An inline comment (`// will be removed in X.Y` or `// to be removed in 3.0`) is strongly encouraged.
- The **deprecated method must delegate to its replacement**, not the other way around. This ensures
  anyone overriding the deprecated method still gets called.
- Add the `deprecation` label to the PR — this drives the automated `🚫 Deprecations` CHANGELOG entry.

### Deprecating default interface methods

The same pattern applies; just add `default` to keep the method callable during the transition:

```java
/**
 * @deprecated Use {@link #configure(IgnoredTypesBuilder)} instead.
 */
@Deprecated
default void configure(IgnoredTypesBuilder builder, ConfigProperties config) {
  configure(builder);
}
```

## What to Flag in Review

- **Breaking change without a prior deprecation**: a method/class was removed or its signature
  changed in a stable module, but there was no `@Deprecated` annotation in the preceding release.
  Flag and ask for the deprecation to be introduced first.

- **Removal of a deprecated item from a stable module before 3.0**: deprecated items in stable
  modules must not be removed in a minor release — they stay until the next major version.

- **`@Deprecated` without Javadoc**: annotation present but no `@deprecated` Javadoc, or the
  Javadoc doesn't name the replacement — ask for both.

- **Wrong delegation direction**: the new method delegates to the old/deprecated one instead of
  the reversed. This breaks overriders of the old method.

- **Deprecated method with new logic**: instead of delegating, it reimplements. The logic should
  live in the new method.

- **Removal PR for things never deprecated**: a removal PR must only remove things that were
  already annotated `@Deprecated` in an earlier release.

- **Missing CHANGELOG entry**: a breaking change PR that does not add an
  `⚠️ Breaking changes to non-stable APIs` bullet in the `Unreleased` section of `CHANGELOG.md`.
