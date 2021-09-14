import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
  `java-library`

  id("net.bytebuddy.byte-buddy")

  id("io.opentelemetry.instrumentation.base")
  id("io.opentelemetry.instrumentation.muzzle-generation")
  id("io.opentelemetry.instrumentation.javaagent-shadowing")
}

dependencies {
  // Integration tests may need to define custom instrumentation modules so we include the standard
  // instrumentation infrastructure for testing too.
  compileOnly("io.opentelemetry.instrumentation:opentelemetry-instrumentation-api")
  compileOnly("io.opentelemetry.javaagent:opentelemetry-javaagent-instrumentation-api")
  compileOnly("io.opentelemetry.javaagent:opentelemetry-javaagent-bootstrap")
  // Apply common dependencies for instrumentation.
  compileOnly("io.opentelemetry.javaagent:opentelemetry-javaagent-extension-api") {
    // OpenTelemetry SDK is not needed for compilation
    exclude(group = "io.opentelemetry", module = "opentelemetry-sdk")
    exclude(group = "io.opentelemetry", module = "opentelemetry-sdk-metrics")
  }
  compileOnly("io.opentelemetry.javaagent:opentelemetry-javaagent-tooling") {
    // OpenTelemetry SDK is not needed for compilation
    exclude(group = "io.opentelemetry", module = "opentelemetry-sdk")
    exclude(group = "io.opentelemetry", module = "opentelemetry-sdk-metrics")
  }

  testImplementation("io.opentelemetry.javaagent:opentelemetry-testing-common")
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
    "-Dotel.javaagent.experimental.initializer.jar=${shadowJar.absolutePath}",
    "-Dotel.javaagent.testing.additional-library-ignores.enabled=false",
    "-Dotel.javaagent.testing.fail-on-context-leak=${findProperty("failOnContextLeak") != false}",
    // prevent sporadic gradle deadlocks, see SafeLogger for more details
    "-Dotel.javaagent.testing.transform-safe-logging.enabled=true",
    // Reduce noise in assertion messages since we don't need to verify this in most tests. We check
    // in smoke tests instead.
    "-Dotel.javaagent.add-thread-details=false",
    "-Dotel.metrics.exporter=otlp"
  )
}

// need to run this after evaluate because testSets plugin adds new test tasks
afterEvaluate {
  tasks.withType<Test>().configureEach {
    val shadowJar = tasks.shadowJar.get()
    val agentShadowJar = agentForTesting.resolve().first()

    dependsOn(shadowJar)
    // TODO(anuraaga): Figure out why dependsOn override is still needed in otel.javaagent-testing
    // despite this dependency.
    dependsOn(agentForTesting.buildDependencies)

    jvmArgumentProviders.add(JavaagentTestArgumentsProvider(agentShadowJar, shadowJar.archiveFile.get().asFile))

    // We do fine-grained filtering of the classpath of this codebase's sources since Gradle's
    // configurations will include transitive dependencies as well, which tests do often need.
    classpath = classpath.filter {
      // The sources are packaged into the testing jar so we need to make sure to exclude from the test
      // classpath, which automatically inherits them, to ensure our shaded versions are used.
      if (file("${buildDir}/resources/main").equals(it) || file("${buildDir}/classes/java/main").equals(it)) {
        return@filter false
      }

      // TODO(anuraaga): Better not to have this folder structure constraints, we can likely use
      // plugin identification instead.

      // If agent depends on some shared instrumentation module that is not a testing module, it will
      // be packaged into the testing jar so we need to make sure to exclude from the test classpath.
      val lib = it.absoluteFile
      val instrumentationDir = file("${rootDir}/instrumentation/").absoluteFile
      if (lib.startsWith(instrumentationDir) &&
        lib.extension == "jar" &&
        !lib.absolutePath.substring(instrumentationDir.absolutePath.length).contains("testing")) {
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
  if (name.toLowerCase().endsWith("testruntimeclasspath")) {
    // Added by agent, don't let Gradle bring it in when running tests.
    exclude("io.opentelemetry.javaagent", "opentelemetry-javaagent-bootstrap")
  }
}
