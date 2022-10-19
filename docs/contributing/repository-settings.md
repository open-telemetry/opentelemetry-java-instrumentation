# Repository settings

(In addition
to https://github.com/open-telemetry/community/blob/main/docs/how-to-configure-new-repository.md)

## General > Pull Requests

* Allow squash merging > Default to pull request title and description

* Allow auto-merge

* Automatically delete head branches: CHECKED

  So automation PR branches will be deleted.

## Actions > General

* Fork pull request workflows from outside collaborators:
  "Require approval for first-time contributors who are new to GitHub"

  To reduce friction for new contributors
  (the default is "Require approval for first-time contributors").

## Brances > Branch protection rules

(In addition
to https://github.com/open-telemetry/community/blob/main/docs/how-to-configure-new-repository.md)

### `main`

* Require branches to be up to date before merging: UNCHECKED

  PR jobs take too long, and leaving this unchecked has not been a significant problem.

* Status checks that are required:

  * EasyCLA
  * required-status-check

### `release/*`

Same settings as above for `main`, except:

* Restrict pushes that create matching branches: UNCHECKED

  So release automation can create release branches.

### `gh-pages`

* Everything UNCHECKED.

  This branch is currently only used for directly pushing benchmarking results from the
  [Nightly overhead benchmark](https://github.com/open-telemetry/opentelemetry-java-instrumentation/actions/workflows/nightly-benchmark-overhead.yml)
  job.

### `dependabot/**/*` and `opentelemetrybot/**/*`

* Require status checks to pass before merging: UNCHECKED

  So bots can rebase their PR branches

* Restrict who can push to matching branches: UNCHECKED

  So bots can create PR branches in the first place

* Allow force pushes > Everyone

  So bots can rebase their PR branches

* Allow deletions: CHECKED

  So bot PR branches can be deleted after merging

### `**/**`

* Status checks that are required:

  EasyCLA

## Tags > Protected tags

### `v*`
