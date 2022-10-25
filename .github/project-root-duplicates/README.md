The two files in this directory are duplicates of the same files in the root directory.

These are needed because we need to exclude the `instrumentation` directory from dependabot since
we intentionally compile and test against old versions of libraries.

And since there is no way to exclude a directory from dependabot
(see https://github.com/dependabot/dependabot-core/issues/4364),
we have to explicitly list the directories that we do want to check
(see ../.github/dependabot.yml)
but that leaves these two files in the root directory unchecked by dependabot.

And so we copy them here to be checked by dependabot inside this directory, and make sure that the
files stay in sync with their copies via a github actions job
(see ../.github/workflows/reusable-common.yml).
