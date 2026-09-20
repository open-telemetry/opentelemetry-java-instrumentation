Replayed all requested source commits in order.

Resolved the module-consolidation conflicts by retaining the base branch's existing in-project unit
test suites while moving the newer target instrumentation and tests into that project.

Validation completed:

- Spotless checks passed.
- Focused unit tests passed for default and stable semantic-convention modes.
- Replay order, commit messages, authors, linear ancestry, and working-tree cleanliness were
  verified.
- Secret scanning found no issues.
- Automated validation reported no findings; its code-review model was unavailable, and CodeQL
  skipped analysis because the database was too large.
