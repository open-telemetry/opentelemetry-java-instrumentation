#!/usr/bin/env bash
#MISE description="Lint markdown files"
#MISE flag "--fix" help="Automatically fix issues"

set -e

if [ "${usage_fix}" = "true" ]; then
  markdownlint-cli2 --fix "**/*.md" "#**/build" "#CHANGELOG.md" "#licenses/licenses.md"
else
  markdownlint-cli2 "**/*.md" "#**/build" "#CHANGELOG.md" "#licenses/licenses.md"
fi
