# Versioning and releasing

OpenTelemetry Auto-Instrumentation for Java uses [SemVer standard](https://semver.org) for versioning of its artifacts.

Instead of manually specifying project version (and by extension the version of built artifacts)
in gradle build scripts, we use [nebula-release-plugin](https://github.com/nebula-plugins/nebula-release-plugin)
to calculate the current version based on git tags. This plugin looks for the latest tag of the form
`vX.Y.Z` on the current branch and calculates the current project version as `vX.Y.(Z+1)-SNAPSHOT`.

## Snapshot builds
Every successful CI build of the master branch automatically executes `./gradlew snapshot` as the last task.
This signals Nebula plugin to build and publish to
[JFrog OSS repository](https://oss.jfrog.org/artifactory/oss-snapshot-local/io/opentelemetry/auto/)next _minor_ release version.
This means version `vX.(Y+1).0-SNAPSHOT`.

## Public releases
All major and minor public releases are initiated by creating a git tag with a version to be released.
Do the following:
- Checkout a branch that you want to release.
- Tag a commit on which you want to base the release by executing `git tag vX.Y.0` with the expected version string.
- Push new tag to upstream repo.

On new tag creation a CI will start a new release build.
It will do the following:
- Checkout requested tag.
- Run `./gradlew -Prelease.useLastTag=true final`.
This signals Nebula plugin to build `X.Y.0` version and to publish it to
[Bintray repository](https://bintray.com/open-telemetry/maven/opentelemetry-java-instrumentation).

## Patch releases
Whenever a fix is needed to any older branch, a PR should be made into the corresponding maintenance branch.
When that PR is merge, CI will notice the new commit into maintenance branch and will initiate a new build for this.
That build, after usual building and checking, will run `./gradlew -Prelease.scope=patch final`.
This will signal Nebula plugin to build a new version `vX.Y.(Z+1)` and publish it to Bintray repo.
