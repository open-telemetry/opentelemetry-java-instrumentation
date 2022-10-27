This directory and the two symlinks in it are used by the
[dependabot configuration](../dependabot.yml), because we can't include the root directory
in the dependabot scanning since then it will pick up all of the old library versions that we
intentionally compile and test against.

See https://github.com/dependabot/dependabot-core/issues/4364.
