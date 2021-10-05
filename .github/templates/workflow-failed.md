---
title: #{{ env.GITHUB_WORKFLOW }} #{{ env.GITHUB_RUN_NUMBER }} failed
labels: bug, area:build, priority:p1
---
<a href="https://github.com/{{ env.GITHUB_REPOSITORY }}/actions/runs/{{ env.GITHUB_RUN_ID }}">
Nightly build #{{ env.GITHUB_RUN_NUMBER }}</a> failed. Please take a look and fix it ASAP.
