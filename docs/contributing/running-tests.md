### Running the tests

#### Java versions

Open Telemetry Auto Instrumentation's minimal supported version is java 8.
All jar files that we produce, unless noted otherwise, have bytecode
compatible with java 8 runtime. Our test suite is executed against
java 8, all LTS versions and the latest non-LTS version.

Some libraries that we auto-instrument may have higher minimal requirements.
In this case we compile and test corresponding auto-instrumentation with
higher java version as required by library. The resulting classes will have
higher bytecode level, but as it matches library's java version, no runtime
problem arise.

#### Instrumentation tests

Executing `./gradlew instrumentation:test` will run tests for all supported
auto-instrumentations using that java version which runs the Gradle build
itself. These tests usually use the minimal supported version of the
instrumented library.

#### Executing tests with specific java version

In order to run tests on a specific java version, just execute `./gradlew
testJava7` (or `testJava11` etc). Then Gradle task
rule will kick in and do the following:

- check, if Gradle already runs on a java with required version
- if not, look for an environment variable named `JAVA_N_HOME`, where `N` is the requested java version
- if Gradle could not found requested java version, then build will fail
- Gradle will now find all corresponding test tasks and configure them to use java executable of the requested version.

#### Executing tests against the latest versions of libraries under instrumentation

This is done as part of the nightly build in order to catch when a new version of a library is
released that breaks our instrumentation tests.

To run these tests locally, add `-PtestLatestDeps=true` to your existing `gradlew` command line.

#### Executing single test

Executing `./gradlew :instrumentation:<INSTRUMENTATION_NAME>:test --tests <GROOVY TEST FILE NAME>` will run only the selected test.
