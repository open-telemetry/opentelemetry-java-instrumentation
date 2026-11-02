# Javaagent instrumentation testing

`:testing:testing-common` provides shared test APIs used by javaagent instrumentation tests.

The central projects keep distinct roles:

- `:testing:javaagent-test-exporters` contains in-memory exporters and helpers loaded by the agent.
- `:testing:javaagent-for-testing` is the testing agent distribution that embeds those exporters.
- `:testing:testing-common` provides shared test APIs and handles the agent class-loader boundary
  for captured telemetry.
- `:testing:testing-common:with-shaded-dependencies` is a dependency wrapper for IDEs and
  Gradle substitution. `:testing:shaded-dependencies` produces the relocated third-party dependency
  bundle consumed by that wrapper and the testing support.

## Running javaagent tests

The repository's `otel.javaagent-testing` plugin configures javaagent tests automatically. Outside
the repository, use the support and testing-agent artifacts from the same release:

```kotlin
import java.io.File
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.testing.Test
import org.gradle.process.CommandLineArgumentProvider

val testAgent = configurations.create("testAgent") {
  isCanBeResolved = true
  isCanBeConsumed = false
}
val testInstrumentation = configurations.create("testInstrumentation") {
  isCanBeResolved = true
  isCanBeConsumed = false
}

dependencies {
  testImplementation("io.opentelemetry.javaagent:opentelemetry-testing-common:$version")
  testAgent("io.opentelemetry.javaagent:opentelemetry-javaagent-for-testing:$version")
  // Declare companion javaagent instrumentation modules needed by these tests
  // in testInstrumentation.
}

// With the Shadow plugin applied, shade this module's instrumentation classes, runtime dependencies,
// and testInstrumentation dependencies. Match the testing agent's package relocations.
tasks.shadowJar {
  configurations = listOf(configurations.runtimeClasspath.get(), testInstrumentation)
  archiveFileName.set("agent-testing.jar")
}

val testAgentJar = testAgent.elements.map { it.first().asFile }
val initializerJar = tasks.shadowJar.flatMap { it.archiveFile }.map { it.asFile }

tasks.withType<Test>().configureEach {
  dependsOn(tasks.shadowJar)
  dependsOn(testAgent.buildDependencies)
  jvmArgumentProviders.add(
    JavaagentTestArguments(testAgentJar, initializerJar)
  )
}

class JavaagentTestArguments(
  @get:InputFile
  @get:PathSensitive(PathSensitivity.RELATIVE)
  val agentJar: Provider<File>,
  @get:InputFile
  @get:PathSensitive(PathSensitivity.RELATIVE)
  val initializerJar: Provider<File>,
) : CommandLineArgumentProvider {
  override fun asArguments(): Iterable<String> =
    listOf(
      "-javaagent:${agentJar.get().absolutePath}",
      "-Dotel.javaagent.experimental.initializer.jar=${initializerJar.get().absolutePath}",
    )
}
```

The `-javaagent` argument points to the `javaagent-for-testing` distribution. The initializer
property instead points to the shaded instrumentation JAR (`agent-testing.jar` in the repository
plugin), not an ordinary test JAR. Keep the two JARs separate and use the same relocations as the
agent.

```text
-javaagent:<javaagent-for-testing.jar>
-Dotel.javaagent.experimental.initializer.jar=<shaded-instrumentation.jar>
```

Adding `opentelemetry-testing-common` alone does not attach an agent. The repository's
`io.opentelemetry.instrumentation.javaagent-testing` convention plugin configures these arguments;
the [`examples/distro` testing-agent project](../../examples/distro/testing/agent-for-testing)
also shows how the testing-agent distribution is assembled.
