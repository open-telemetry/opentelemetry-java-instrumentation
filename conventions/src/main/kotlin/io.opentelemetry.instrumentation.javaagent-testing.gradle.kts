plugins {
  `java-library`

  id("io.opentelemetry.instrumentation.base")
  id("io.opentelemetry.instrumentation.muzzle-generation")
  id("io.opentelemetry.instrumentation.javaagent-shadowing")
}

dependencies {
  /*
    Dependencies added to this configuration will be found by the muzzle gradle plugin during code
    generation phase. These classes become part of the code that plugin inspects and traverses during
    references collection phase.
   */
  add("codegen", "io.opentelemetry.javaagent:opentelemetry-javaagent-tooling")
}

dependencies {
  // Integration tests may need to define custom instrumentation modules so we include the standard
  // instrumentation infrastructure for testing too.
  compileOnly("io.opentelemetry.instrumentation:opentelemetry-instrumentation-api")
  compileOnly("io.opentelemetry.instrumentation:opentelemetry-instrumentation-api-incubator")
  compileOnly("io.opentelemetry.javaagent:opentelemetry-javaagent-bootstrap")
  // Apply common dependencies for instrumentation.
  compileOnly("io.opentelemetry.javaagent:opentelemetry-javaagent-extension-api") {
    // OpenTelemetry SDK is not needed for compilation
    exclude(group = "io.opentelemetry", module = "opentelemetry-sdk")
  }
  compileOnly("io.opentelemetry.javaagent:opentelemetry-javaagent-tooling") {
    // OpenTelemetry SDK is not needed for compilation
    exclude(group = "io.opentelemetry", module = "opentelemetry-sdk")
  }

  // Used by byte-buddy but not brought in as a transitive dependency
  compileOnly("com.google.code.findbugs:annotations")
}

testing {
  suites.withType(JvmTestSuite::class).configureEach {
    dependencies {
      implementation("io.opentelemetry.javaagent:opentelemetry-testing-common")
    }
  }
}

val testInstrumentation by configurations.creating {
  isCanBeConsumed = false
  isCanBeResolved = true
}

tasks.shadowJar {
  configurations = listOf(project.configurations.runtimeClasspath.get(), testInstrumentation)

  archiveFileName.set("agent-testing.jar")
}

val agentForTesting by configurations.creating {
  isCanBeConsumed = false
  isCanBeResolved = true
}

dependencies {
  agentForTesting("io.opentelemetry.javaagent:opentelemetry-agent-for-testing")
}

class JavaagentTestArgumentsProvider(
  @InputFile
  @PathSensitive(PathSensitivity.RELATIVE)
  val agentShadowJar: File,

  @InputFile
  @PathSensitive(PathSensitivity.RELATIVE)
  val shadowJar: File,
) : CommandLineArgumentProvider {
  override fun asArguments(): Iterable<String> = listOf(
    "-Dotel.javaagent.debug=true",
    "-javaagent:${agentShadowJar.absolutePath}",
    // make the path to the javaagent available to tests
    "-Dotel.javaagent.testing.javaagent-jar-path=${agentShadowJar.absolutePath}",
    "-Dotel.javaagent.experimental.initializer.jar=${shadowJar.absolutePath}",
    "-Dotel.javaagent.testing.additional-library-ignores.enabled=false",
    "-Dotel.javaagent.testing.fail-on-context-leak=${findProperty("failOnContextLeak") != false}",
    // prevent sporadic gradle deadlocks, see SafeLogger for more details
    "-Dotel.javaagent.testing.transform-safe-logging.enabled=true",
    // Reduce noise in assertion messages since we don't need to verify this in most tests. We check
    // in smoke tests instead.
    "-Dotel.javaagent.add-thread-details=false",
    "-Dotel.javaagent.experimental.indy=${findProperty("testIndy") == "true"}",
    // suppress repeated logging of "No metric data to export - skipping export."
    // since PeriodicMetricReader is configured with a short interval
    "-Dio.opentelemetry.javaagent.slf4j.simpleLogger.log.io.opentelemetry.sdk.metrics.export.PeriodicMetricReader=INFO",
    // suppress a couple of verbose ClassNotFoundException stack traces logged at debug level
    "-Dio.opentelemetry.javaagent.slf4j.simpleLogger.log.io.grpc.internal.ServerImplBuilder=INFO",
    "-Dio.opentelemetry.javaagent.slf4j.simpleLogger.log.io.grpc.internal.ManagedChannelImplBuilder=INFO",
    "-Dio.opentelemetry.javaagent.slf4j.simpleLogger.log.io.perfmark.PerfMark=INFO",
    "-Dio.opentelemetry.javaagent.slf4j.simpleLogger.log.io.grpc.Context=INFO",
    "-Dotel.java.experimental.span-attributes.copy-from-baggage.include=test-baggage-key-1,test-baggage-key-2"
  )
}

// need to run this after evaluate because testSets plugin adds new test tasks
afterEvaluate {
  tasks.withType<Test>().configureEach {
    val shadowJar = tasks.shadowJar.get()
    val agentShadowJar = agentForTesting.resolve().first()

    dependsOn(shadowJar)
    // TODO: Figure out why dependsOn override is still needed in otel.javaagent-testing despite
    // this dependency.
    dependsOn(agentForTesting.buildDependencies)

    jvmArgumentProviders.add(
      JavaagentTestArgumentsProvider(
        agentShadowJar,
        shadowJar.archiveFile.get().asFile
      )
    )

    // We do fine-grained filtering of the classpath of this codebase's sources since Gradle's
    // configurations will include transitive dependencies as well, which tests do often need.
    classpath = classpath.filter {
      if (file(layout.buildDirectory.dir("resources/main")).equals(it) || file(
          layout.buildDirectory.dir(
            "classes/java/main"
          )
        ).equals(it)
      ) {
        // The sources are packaged into the testing jar, so we need to exclude them from the test
        // classpath, which automatically inherits them, to ensure our shaded versions are used.
        return@filter false
      }

      // TODO: Better not to have this naming constraint, we can likely use plugin identification
      // instead.

      val lib = it.absoluteFile
      if (lib.name.startsWith("opentelemetry-javaagent-")) {
        // These dependencies are packaged into the testing jar, so we need to exclude them from the test
        // classpath, which automatically inherits them, to ensure our shaded versions are used.
        return@filter false
      }
      if (lib.name.startsWith("opentelemetry-") && lib.name.contains("-autoconfigure-")) {
        // These dependencies should not be on the test classpath, because they will auto-instrument
        // the library and the tests could pass even if the javaagent instrumentation fails to apply
        return@filter false
      }
      return@filter true
    }
  }
}

// shadowJar is only used for creating a jar for testing, but the shadow plugin automatically adds
// it to a project's published Java component. Skip it if publishing is configured for this
// project.
plugins.withId("maven-publish") {
  configure<PublishingExtension> {
    (components["java"] as AdhocComponentWithVariants).run {
      withVariantsFromConfiguration(configurations["shadowRuntimeElements"]) {
        skip()
      }
    }
  }
}

configurations.configureEach {
  if (name.endsWith("testruntimeclasspath", ignoreCase = true)) {
    // Added by agent, don't let Gradle bring it in when running tests.
    exclude("io.opentelemetry.javaagent", "opentelemetry-javaagent-bootstrap")
  }
}
