## Contributing

Pull requests for bug fixes are welcome, but before submitting new features
or changes to current functionality [open an
issue](https://github.com/open-telemetry/opentelemetry-java-instrumentation/issues/new)
and discuss your ideas or propose the changes you wish to make. After a
resolution is reached a PR can be submitted for review.

In order to build and test this whole repository you need JDK 11+.
Some instrumentations and tests may put constraints on which java versions they support.
See [Running the tests](./docs/contributing/running-tests.md) for more details.

### Building

#### Snapshot builds

For developers testing code changes before a release is complete, there are
snapshot builds of the `master` branch. They are available from
[JFrog OSS repository](https://oss.jfrog.org/artifactory/oss-snapshot-local/io/opentelemetry/instrumentation/)

#### Building from source

Build using Java 11:

```bash
java -version
```

```bash
./gradlew assemble
```

and then generate the -all artifact

```bash
./gradlew :javaagent:shadowJar
```

and then you can find the java agent artifact at

`javaagent/build/lib/opentelemetry-javaagent-<version>-all.jar`.

### IntelliJ setup

See [IntelliJ setup](docs/contributing/intellij-setup.md)

### Style guide

See [Style guide](docs/contributing/style-guideline.md)

### Running the tests

See [Running the tests](docs/contributing/running-tests.md)

### Writing instrumentation

See [Writing instrumentation](docs/contributing/writing-instrumentation.md)

### Understanding the javaagent components

See [Understanding the javaagent components](docs/contributing/javaagent-jar-components.md)

### Debugging

See [Debugging](docs/contributing/debugging.md)

### Maintainers, Approvers and Triagers

Maintainers:

- [Anuraag Agrawal](https://github.com/anuraaga), AWS
- [Nikita Salnikov-Tarnovski](https://github.com/iNikem), Splunk
- [Trask Stalnaker](https://github.com/trask), Microsoft
- [Tyler Benson](https://github.com/tylerbenson), DataDog

Approvers:

- [John Watson](https://github.com/jkwatson), Splunk

Triagers:

- [Sergei Malafeev](https://github.com/malafeev), Lightstep

#### Become a Triager, Approver or Maintainer

See the [community membership document](https://github.com/open-telemetry/community/blob/master/community-membership.md)
in OpenTelemetry community repo.
