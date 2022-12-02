# Repository settings

Repository settings in addition to what's documented already at
<https://github.com/open-telemetry/community/blob/main/docs/how-to-configure-new-repository.md>.

## General > Pull Requests

* Allow squash merging > Default to pull request title and description

* Allow auto-merge

* Automatically delete head branches: CHECKED

  (So that bot PR branches will be deleted)

## Actions > General

* Fork pull request workflows from outside collaborators:
  "Require approval for first-time contributors who are new to GitHub"

  (To reduce friction for new contributors,
  as the default is "Require approval for first-time contributors")

## Branch protections

### `main`

* Require branches to be up to date before merging: UNCHECKED

  (PR jobs take too long, and leaving this unchecked has not been a significant problem)

* Status checks that are required:

  * EasyCLA
  * required-status-check

### `release/*`

Same settings as above for `main`, except:

* Restrict pushes that create matching branches: UNCHECKED

  (So that opentelemetrybot can create release branches)

### `gh-pages`

* Everything UNCHECKED.

  (This branch is currently only used for directly pushing benchmarking results from the
  [Nightly overhead benchmark](https://github.com/open-telemetry/opentelemetry-java-instrumentation/actions/workflows/nightly-benchmark-overhead.yml)
  job)

### `dependabot/**/**` and `opentelemetrybot/*`

* Require a pull request before merging: UNCHECKED

  (So that these PR branches can be updated directly)

* Require status checks to pass before merging: UNCHECKED

  (N/A since these branches are updated directly and not merged into)

* Restrict who can push to matching branches: UNCHECKED

  (So that dependabot and GitHub Action's
  [`GITHUB_TOKEN`](https://docs.github.com/en/actions/security-guides/automatic-token-authentication)
  can create PR branches in this repository)

* Allow force pushes > Everyone

  (So that dependabot PRs can be rebased, which requires a force push)

* Allow deletions: CHECKED

  (So that these branches can be deleted after corresponding PR is merged)

### `**/**`

* Status checks that are required:

  EasyCLA
