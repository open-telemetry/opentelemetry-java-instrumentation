# Contributing

Pull requests for bug fixes are always welcome!

Before submitting new features or changes to current functionality, it is recommended to first
[open an issue](https://github.com/open-telemetry/opentelemetry-java-instrumentation/issues/new)
and discuss your ideas or propose the changes you wish to make.

## Changelog

The changelog is assembled by [Towncrier](https://towncrier.readthedocs.io/) from Markdown
fragments in [changelog.d](changelog.d). Add a fragment for every user-visible change.
Changes limited to tests, documentation, CI, or implementation-only refactors do not need one.
Do not edit `CHANGELOG.md` directly; release preparation generates it from the fragments.

Name the file `<PR-number>.<type>.md`, for example `12345.bugfix.md`. Its contents should be
a short description of the effect on users, without a bullet prefix or PR link. Towncrier
adds those when rendering the changelog. Include migration guidance for breaking changes
and name replacement APIs or configuration for deprecations.

| Type | Changelog section |
| --- | --- |
| `breaking` | Breaking changes to stable APIs, configuration, or telemetry |
| `alpha-breaking` | Breaking changes to non-stable APIs |
| `deprecation` | Deprecations |
| `feature` | Enhancements, including new features and instrumentation |
| `bugfix` | Bug fixes |

Use `12345.bugfix.1.md` for another note of the same type in one PR. If the PR number is
not available yet, start with a unique name such as `+fix-http-spans.bugfix.md` and rename
it once the PR is open. Fragments starting with `+` render without an automatically
generated PR link.

For example, `12345.bugfix.md` could contain:

```markdown
Fix missing HTTP client spans when a request fails before receiving a response.
```

To preview pending release notes without modifying files:

```bash
python -m pip install -r .github/scripts/changelog/requirements.txt
python -m towncrier build --draft --version 3.0.0
```

Use the upcoming release version in the preview command. Copilot and human reviewers check
whether a fragment is needed and whether it accurately describes the change; there is no
required fragment-presence CI check.

## Breaking Changes

When your PR introduces a breaking change:

- Add a `breaking` or `alpha-breaking` fragment to `changelog.d`
- Provide migration notes in the PR description:
  - What is changing and why
  - How users should update their code/configuration
  - Code examples showing before/after usage (if applicable)

**When to Use:**

- API changes that break backward compatibility
- Configuration changes that require user action
- Behavioral changes that might affect existing users
- Removal of deprecated features

## Deprecations

When your PR deprecates functionality:

- Add a `deprecation` fragment to `changelog.d`
- Provide deprecation details in the PR description:
  - What is being deprecated and why
  - What should be used instead (if applicable)
  - Timeline for removal (if known)
  - Any migration guidance

## Building

This project requires Java 25 to build and run tests. Newer JDK's may work, but
this version is used in CI.

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

Build using Java 25:

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

See [Style guide](docs/contributing/style-guide.md)

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
