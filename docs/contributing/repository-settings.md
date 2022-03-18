# Repository settings

(In addition to https://github.com/open-telemetry/community/blob/main/docs/how-to-configure-new-repository.md)

* Automatically delete head branches: CHECKED

  So that bot PR branches will be deleted.

## Branch protections

(In addition to https://github.com/open-telemetry/community/blob/main/docs/how-to-configure-new-repository.md)

### `main` and `v*`

* Require branches to be up to date before merging: UNCHECKED

  PR jobs take too long, and leaving this unchecked has not been a significant problem.

* Status checks that are required:

  * EasyCLA
  * accept-pr

### `gh-pages`

* Everything UNCHECKED.

  This branch is currently only used for directly pushing benchmarking results from the
  [Nightly overhead benchmark](https://github.com/open-telemetry/opentelemetry-java-instrumentation/actions/workflows/nightly-benchmark-overhead.yml)
  job.

### `**/**`

* Status checks that are required:

  EasyCLA

* Allow deletions: CHECKED

  So that bot PR branches can be deleted
