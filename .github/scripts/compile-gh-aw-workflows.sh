#!/bin/bash -e

# Compiles the agentic workflow (gh-aw) sources into their generated lockfiles.
#
# The CI check and the lockfile auto-update workflow both run this script, so that they always use
# the same pinned compiler and the same options. Compiling them differently produces generated files
# that the CI check then immediately rewrites, which fails the build.
#
# Always compile all gh-aw workflows. The compiler is pinned via `gh extension install --pin` and
# `--no-check-update`, and action SHAs are pinned via .github/aw/actions-lock.json, so the output is
# deterministic for a given pinned version. With no arguments, both `gh aw validate` and
# `gh aw compile` operate on every Markdown file in .github/workflows. Compiling all workflows takes
# only a couple of seconds.
#
# Pass --approve when regenerating after a compiler upgrade. That approves the safe manifest changes
# (e.g. added or removed actions) that strict mode otherwise refuses. It does not affect which
# actions the compiler generates, so the output still matches what the CI check produces.

gh extension install github/gh-aw --pin v0.84.3

gh aw validate --no-check-update

gh aw compile --no-check-update "$@"
