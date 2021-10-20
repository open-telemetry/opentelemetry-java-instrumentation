# Versioning and releasing

OpenTelemetry Auto-Instrumentation for Java uses [SemVer standard](https://semver.org) for versioning of its artifacts.

Instead of manually specifying project version (and by extension the version of built artifacts)
in gradle build scripts, we use [nebula-release-plugin](https://github.com/nebula-plugins/nebula-release-plugin)
to calculate the current version based on git tags. This plugin looks for the latest tag of the form
`vX.Y.Z` on the current branch and calculates the current project version as `vX.Y.(Z+1)-SNAPSHOT`.

## Snapshot builds
Every successful CI build of the master branch automatically executes `./gradlew snapshot` as the last task.
This signals Nebula plugin to build and publish to
[Sonatype OSS snapshots repository](https://oss.sonatype.org/content/repositories/snapshots/io/opentelemetry/)
next _minor_ release version. This means version `vX.(Y+1).0-SNAPSHOT`.

## Starting the Release

Before making the release:

* Update `CHANGELOG.md`
* Update the version (remove `-SNAPSHOT`) in these files:
  * version.gradle.kts
  * examples/distro/build.gradle
  * examples/extension/build.gradle

Open the release build workflow in your browser [here](https://github.com/open-telemetry/opentelemetry-java-instrumentation/actions/workflows/release-build.yml).

You will see a button that says "Run workflow". Press the button, enter the version number you want
to release in the input field that pops up, and then press "Run workflow".

This triggers the release process, which builds the artifacts, publishes the artifacts, and creates
and pushes a git tag with the version number.

After making the release:

* Update the version (bump and add `-SNAPSHOT`) in these files:
  * version.gradle.kts
  * examples/distro/build.gradle
  * examples/extension/build.gradle

## Announcement

Once the GitHub workflow completes, go to Github [release
page](https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases), press
`Draft a new release` to write release notes about the new release. If there is already a draft
release notes, just point it at the created tag.

## Patch Release

All patch releases should include only bug-fixes, and must avoid
adding/modifying the public APIs.

In general, patch releases are only made for bug-fixes for the following types of issues:
* Regressions
* Memory leaks
* Deadlocks

To make a patch release, open the patch release build workflow in your browser
[here](https://github.com/open-telemetry/opentelemetry-java-instrumentation/actions/workflows/patch-release-build.yml).

You will see a button that says "Run workflow". Press the button, enter the version number you want
to release in the input field for version that pops up and the commits you want to cherrypick.
If you are entering multiple commits, they should be separated by spaces. Then, press "Run workflow".

The automated branch creation will fail if any of the yaml files differ between the release branch
and `main`:

```
Switched to a new branch 'v1.6.x'
To https://github.com/open-telemetry/opentelemetry-java-instrumentation
! [remote rejected]     v1.6.x -> v1.6.x (refusing to allow a GitHub App to create or update workflow `.github/workflows/pr-smoke-test-fake-backend-images.yml` without `workflows` permission)
```

and you will need to manually create it before proceeding, e.g.

```
git checkout -b v1.6.x v1.6.0
git push upstream v1.6.x
```

If the commits cannot be cleanly applied to the release branch, for example because it has diverged
too much from main, then the workflow will fail before building. In this case, you will need to
prepare the release branch manually.

This example will assume patching into release branch `v1.6.x` from a git repository with remotes
named `origin` and `upstream`.

```
$ git remote -v
origin	git@github.com:username/opentelemetry-java.git (fetch)
origin	git@github.com:username/opentelemetry-java.git (push)
upstream	git@github.com:open-telemetry/opentelemetry-java.git (fetch)
upstream	git@github.com:open-telemetry/opentelemetry-java.git (push)
```

First, checkout the release branch

```
git fetch upstream v1.6.x
git checkout upstream/v1.6.x
```

Apply cherrypicks manually and commit. It is ok to apply multiple cherrypicks in a single commit.
Use a commit message such as "Manual cherrypick for commits commithash1, commithash2".

After committing the change, push to your fork's branch.

```
git push origin v1.6.x
```

Create a PR to have code review and merge this into upstream's release branch. As this was not
applied automatically, we need to do code review to make sure the manual cherrypick is correct.

After it is merged, Run the patch release workflow again, but leave the commits input field blank.
The release will be made with the current state of the release branch, which is what you prepared
above.
