# Contributing

Pull requests for bug fixes are always welcome!

Before submitting new features or changes to current functionality, it is recommended to first
[open an issue](https://github.com/open-telemetry/opentelemetry-java-instrumentation/issues/new)
and discuss your ideas or propose the changes you wish to make.


## Breaking Changes

When your PR introduces a breaking change:

* Add the `breaking change` label to your PR
  - If you can't add labels directly, post a comment containing only `/breaking-change` and the label will be added automatically
  - To remove the label, post a comment containing only `/remove-breaking-change`
* Provide migration notes in the PR description:
  - What is changing and why
  - How users should update their code/configuration
  - Code examples showing before/after usage (if applicable)

**When to Use:**

* API changes that break backward compatibility
* Configuration changes that require user action
* Behavioral changes that might affect existing users
* Removal of deprecated features

## Deprecations

When your PR deprecates functionality:

* Add the `deprecation` label to your PR
  - If you can't add labels directly, post a comment containing only `/deprecation` and the label will be added automatically
  - To remove the label, post a comment containing only `/remove-deprecation`
* Provide deprecation details in the PR description:
  - What is being deprecated and why
  - What should be used instead (if applicable)
  - Timeline for removal (if known)
  - Any migration guidance

## Building

This project requires Java 21 to build and run tests. Newer JDK's may work, but this version is used in CI.

Some instrumentations and tests may put constraints on which java versions they support.
See [Running the tests](./docs/contributing/running-tests.md) for more details.

### Snapshot builds

For developers testing code changes before a release is complete, snapshot builds of the `main`
branch are available from the Sonatype snapshot repository at `https://central.sonatype.com/repository/maven-snapshots/`.

To find the latest snapshot, check the maven metadata (replace `{LATEST_VERSION}` with the current
stable release):

```
https://central.sonatype.com/repository/maven-snapshots/io/opentelemetry/javaagent/opentelemetry-javaagent/{LATEST_VERSION}-SNAPSHOT/maven-metadata.xml
```

Look for the `<timestamp>` and `<buildNumber>` in the XML response, then construct the download URL:

```
https://central.sonatype.com/repository/maven-snapshots/io/opentelemetry/javaagent/opentelemetry-javaagent/{VERSION}-SNAPSHOT/opentelemetry-javaagent-{VERSION}-{TIMESTAMP}-{BUILD_NUMBER}.jar
```

For example, if the metadata shows timestamp `20250925.160708` and build number `56` for version
`2.21.0`, the snapshot JAR URL would be:

```
https://central.sonatype.com/repository/maven-snapshots/io/opentelemetry/javaagent/opentelemetry-javaagent/2.21.0-SNAPSHOT/opentelemetry-javaagent-2.21.0-20250925.160708-56.jar
```

### Building from source

Build using Java 21:

```bash
java -version
```

```bash
./gradlew assemble
```

and then you can find the java agent artifact at

`javaagent/build/libs/opentelemetry-javaagent-<version>.jar`.

To simplify local development, you can remove the version number from the build product. This allows
the file name to stay consistent across versions. To do so, add the following to
`~/.gradle/gradle.properties`.

```properties
removeJarVersionNumbers=true
```

## Working with fork repositories

If you forked this repository, some GitHub Actions workflows may fail due to missing secrets or permissions. To avoid unnecessary workflow failure notifications:

### Disabling GitHub Actions in your fork

**Option 1: Disable all workflows** - Go to Settings > Actions > General, select "Disable actions", and save

**Option 2: Disable specific workflows** - Go to Actions tab, click a workflow, click "..." menu, and select "Disable workflow"

Either option still allows you to contribute via pull requests to the main repository.

## IntelliJ setup and troubleshooting

See [IntelliJ setup and troubleshooting](docs/contributing/intellij-setup-and-troubleshooting.md)

## Style guide

See [Style guide](docs/contributing/style-guideline.md)

## Running the tests

See [Running the tests](docs/contributing/running-tests.md)

## Writing instrumentation

See [Writing instrumentation](docs/contributing/writing-instrumentation.md)

## Understanding the javaagent structure

See [Understanding the javaagent structure](docs/contributing/javaagent-structure.md)

## Understanding the javaagent instrumentation testing components

See [Understanding the javaagent instrumentation testing components](docs/contributing/javaagent-test-infra.md)

## Debugging

See [Debugging](docs/contributing/debugging.md)

## Understanding Muzzle

See [Understanding Muzzle](docs/contributing/muzzle.md)

## Troubleshooting PR build failures

The build logs are very long and there is a lot of parallelization, so the logs can be hard to
decipher, but if you expand the "Build scan" step, you should see something like:

```text
Run cat build-scan.txt
https://gradle.com/s/ila4qwp5lcf5s
```

Opening the build scan link can sometimes take several seconds (it's a large build), but it
typically makes it a lot clearer what's failing. Sometimes there will be several build scans in a
log, so look for one that follows the "BUILD FAILED" message.

You can also try the "Explain error" button at the top of the GitHub Actions page,
which often does a reasonable job of parsing the long build log and displaying the important part.

### Draft PRs

Draft PRs are welcome, especially when exploring new ideas or experimenting with a hypothesis.
However, draft PRs may not receive the same degree of attention, feedback, or scrutiny unless
requested directly. In order to help keep the PR backlog maintainable, drafts older than 6 months
will be closed by the project maintainers. This should not be interpreted as a rejection. Closed
PRs may be reopened by the author when time or interest allows.
