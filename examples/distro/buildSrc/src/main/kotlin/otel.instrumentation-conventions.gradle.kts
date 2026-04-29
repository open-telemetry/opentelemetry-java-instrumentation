plugins {
  id("otel.java-conventions")
  id("com.gradleup.shadow")
}

val testInstrumentation by configurations.creating
val testAgent by configurations.creating

dependencies {
  compileOnly("io.opentelemetry:opentelemetry-sdk")
  compileOnly("io.opentelemetry.instrumentation:opentelemetry-instrumentation-api")
  compileOnly("io.opentelemetry.javaagent:opentelemetry-javaagent-extension-api")

  annotationProcessor("com.google.auto.service:auto-service:$autoserviceVersion")
  compileOnly("com.google.auto.service:auto-service-annotations:$autoserviceVersion")

  // the javaagent that is going to be used when running instrumentation unit tests
  testAgent(project(path = ":testing:agent-for-testing", configuration = "shadow"))
  // test dependencies
  testImplementation("io.opentelemetry.javaagent:opentelemetry-testing-common")
  testImplementation("io.opentelemetry:opentelemetry-sdk-testing")
  testImplementation("org.assertj:assertj-core:3.27.7")
}

tasks.shadowJar {
  configurations = listOf(project.configurations.runtimeClasspath.get(), testInstrumentation)

  mergeServiceFiles()
  // mergeServiceFiles requires that duplicate strategy is set to include
  filesMatching("META-INF/services/**") {
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
  }

  archiveFileName.set("agent-testing.jar")

  relocatePackages(this)
}

tasks.withType<Test>().configureEach {
  val initializerJar = tasks.shadowJar.flatMap { it.archiveFile }.map { it.asFile }
  val agentJar = testAgent.elements.map { it.first().asFile }

  jvmArgs("-Dotel.javaagent.debug=true")
  jvmArgs("-Dotel.javaagent.testing.additional-library-ignores.enabled=false")
  jvmArgs("-Dotel.javaagent.testing.fail-on-context-leak=true")
  // prevent sporadic gradle deadlocks, see SafeLogger for more details
  jvmArgs("-Dotel.javaagent.testing.transform-safe-logging.enabled=true")

  jvmArgumentProviders.add(JavaagentProvider(agentJar, initializerJar))

  // The sources are packaged into the testing jar so we need to make sure to exclude from the test
  // classpath, which automatically inherits them, to ensure our shaded versions are used.
  val resourcesMain = layout.buildDirectory.dir("resources/main").get().asFile
  val classesMain = layout.buildDirectory.dir("classes/java/main").get().asFile
  classpath = classpath.filter {
    it != resourcesMain && it != classesMain
  }
}

class JavaagentProvider(
  @get:InputFile
  @get:PathSensitive(PathSensitivity.RELATIVE)
  val agentJar: Provider<File>,
  @get:InputFile
  @get:PathSensitive(PathSensitivity.RELATIVE)
  val initializerJar: Provider<File>,
) : CommandLineArgumentProvider {
  override fun asArguments(): Iterable<String> = listOf(
    "-javaagent:${agentJar.get().absolutePath}",
    "-Dotel.javaagent.experimental.initializer.jar=${initializerJar.get().absolutePath}",
  )
}
