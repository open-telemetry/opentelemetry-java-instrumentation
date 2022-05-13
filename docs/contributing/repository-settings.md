# Repository settings

(In addition to https://github.com/open-telemetry/community/blob/main/docs/how-to-configure-new-repository.md)

## General

* Automatically delete head branches: CHECKED

  So that bot PR branches will be deleted.

## Actions > General

* Fork pull request workflows from outside collaborators:
  "Require approval for first-time contributors who are new to GitHub"

  To reduce friction for new contributors
  (the default is "Require approval for first-time contributors").

## Branch protections

(In addition to https://github.com/open-telemetry/community/blob/main/docs/how-to-configure-new-repository.md)

### `main` and `release/*`

* Require branches to be up to date before merging: UNCHECKED

  PR jobs take too long, and leaving this unchecked has not been a significant problem.

* Status checks that are required:

  * EasyCLA
  * required-status-check

### `v*` (old release branches)

Same settings as above for new release branches (`release/**`), except:

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

## Tag protections

* `v*`

  To prevent accidents. Though sometimes useful for release snafu, so may reconsider if
  maintainers lose admin rights.
