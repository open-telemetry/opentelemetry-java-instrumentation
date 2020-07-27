### Running the tests

#### Java versions

Open Telemetry Auto Instrumentation's minimal supported version is java 7.
All jar files that we produce, unless noted otherwise, have bytecode
compatible with java 7 runtime. In addition to that we test our code with all
later java versions as well: from 8 to 14.

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

In addition to that each instrumentation has a separate test set called
`latestDepTest`. It was created by [Gradle test sets
plugin](https://github.com/unbroken-dome/gradle-testsets-plugin). It uses the
very same tests as before, but declares a dynamic dependency on the latest
available version of this library. You can run them all by executing
`./gradlew latestDepTest`.

#### Executing tests with specific java version

In order to run tests on a specific java version, just execute `./gradlew
testJava7` (or `testJava11` or `latestDepTestJava14` etc). Then Gradle task
rule will kick in and do the following:

- check, if Gradle already runs on a java with required version
- if not, look for an environment variable named `JAVA_N_HOME`, where `N` is the requested java version
- if Gradle could not found requested java version, then build will fail
- Gradle will now find all corresponding test tasks and configure them to use java executable of the requested version.

This works both for tasks named `test` and `latestDepTest`. But currently
does not work for other custom test tasks, such as those created by test sets
plugin.
