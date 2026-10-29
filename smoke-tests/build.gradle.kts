import java.time.Duration

plugins {
  id("otel.java-conventions")
}

description = "smoke-tests"

val appServerSuites = mapOf(
  "payara" to "**/Payara*.*",
  "jetty" to "**/Jetty*.*",
  "liberty" to "**/Liberty*.*",
  "tomcat" to "**/Tomcat*.*",
  "tomee" to "**/Tomee*.*",
  "websphere" to "**/Websphere*.*",
  "wildfly" to "**/Wildfly*.*",
)
val suites = appServerSuites + ("spring-aot" to "**/SpringBootAotSmokeTest.*")
val smokeTestSuite = project.findProperty("smokeTestSuite") as String?
val reducedAppServerTests = project.findProperty("reducedAppServerTests") == "true"

otelJava {
  minJavaVersionSupported.set(JavaVersion.VERSION_11)
  maxJavaVersionForTests.set(JavaVersion.VERSION_11)
}
val dockerJavaVersion = "3.7.1"
dependencies {
  compileOnly("com.google.auto.value:auto-value-annotations")
  annotationProcessor("com.google.auto.value:auto-value")

  api("io.opentelemetry.javaagent:opentelemetry-testing-common")

  implementation(platform("io.grpc:grpc-bom:1.85.1"))
  implementation("org.slf4j:slf4j-api")
  implementation("io.opentelemetry:opentelemetry-api")
  implementation("io.opentelemetry.proto:opentelemetry-proto")
  implementation("org.testcontainers:testcontainers")
  implementation("com.fasterxml.jackson.core:jackson-databind")
  implementation("com.google.protobuf:protobuf-java-util:4.36.2")
  implementation("io.grpc:grpc-netty-shaded")
  implementation("io.grpc:grpc-protobuf")
  implementation("io.grpc:grpc-stub")

  implementation("com.github.docker-java:docker-java-core:$dockerJavaVersion")
  implementation("com.github.docker-java:docker-java-transport-httpclient5:$dockerJavaVersion")
}

tasks {
  // makes the generated declarative configuration example available on the test classpath, so that
  // DeclarativeConfigurationExampleSmokeTest can verify that the agent starts up with it
  processTestResources {
    from(rootProject.file("docs/declarative-configuration-example.yaml"))
  }

  withType<Test>().configureEach {
    testLogging.showStandardStreams = true

    // this needs to be long enough so that smoke tests that are just running slow don't time out
    timeout.set(Duration.ofMinutes(60))

    // We enable/disable smoke tests based on the java version requests
    // In addition to that we disable them on normal test task to only run when explicitly requested.
    enabled = enabled && gradle.startParameter.taskNames.any { it.startsWith(":smoke-tests:") }

    inputs.property(
      "useWindowsContainers",
      project.providers.systemProperty("os.name")
        .zip(project.providers.environmentVariable("USE_LINUX_CONTAINERS").orElse("")) { os, linux ->
          linux != "1" && os.lowercase().contains("windows")
        },
    )

    val appServerGroup = when (name) {
      "testRepresentativeAppServers" -> "representative"
      "testRemainingAppServers" -> "remaining"
      else -> null
    }
    val includesAppServers = smokeTestSuite == null || smokeTestSuite in appServerSuites
    when {
      smokeTestSuite == null -> {
        if (appServerGroup != null) {
          include(appServerSuites.values)
        }
      }

      smokeTestSuite in suites -> include(suites.getValue(smokeTestSuite!!))

      smokeTestSuite == "other" -> exclude(suites.values)

      smokeTestSuite == "none" -> exclude("**/*")

      else -> throw GradleException("Unknown smoke test suite: $smokeTestSuite")
    }
    if (appServerGroup != null && !includesAppServers) {
      // An empty partition must not turn into an unfiltered Test task.
      exclude("**/*")
    }
    if (includesAppServers) {
      if (appServerGroup != null) {
        systemProperty("appServerTestGroup", appServerGroup)
      }
      if (
        findProperty("skipOpenJ9SmokeTests") == "true" &&
        !reducedAppServerTests &&
        smokeTestSuite != "websphere"
      ) {
        exclude("**/*Openj9*.*")
      }
    }

    val shadowTask = project(":javaagent").tasks.named<Jar>("shadowJar")
    val agentJarPath = shadowTask.flatMap { it.archiveFile }
    inputs.files(agentJarPath)
      .withPropertyName("javaagent")
      .withNormalizer(ClasspathNormalizer::class)

    val extensionTask = project(":smoke-tests:extensions:extension").tasks.named<Jar>("jar")
    val extensionJarPath = extensionTask.flatMap { it.archiveFile }

    val extensionTestAppTask = project(":smoke-tests:extensions:testapp").tasks.named<Jar>("jar")
    val extensionTestAppJarPath = extensionTestAppTask.flatMap { it.archiveFile }

    val legacySpringBootTask = project(":smoke-tests:images:spring-boot-1.3").tasks.named<Jar>("jar")
    val legacySpringBootJarPath = legacySpringBootTask.flatMap { it.archiveFile }
    inputs.files(extensionJarPath).withPropertyName("smokeTestExtension")
    inputs.files(extensionTestAppJarPath)
      .withPropertyName("smokeTestExtensionApp")
      .withNormalizer(ClasspathNormalizer::class)
    inputs.files(legacySpringBootJarPath)
      .withPropertyName("legacySpringBootLogCorrelationFixture")
      .withNormalizer(ClasspathNormalizer::class)

    dependsOn(shadowTask, extensionTestAppTask, extensionTask, legacySpringBootTask)

    doFirst {
      jvmArgs(
        "-Dio.opentelemetry.smoketest.agent.shadowJar.path=${agentJarPath.get()}",
        "-Dio.opentelemetry.smoketest.extension.path=${extensionJarPath.get()}",
        "-Dio.opentelemetry.smoketest.extension.testapp.path=${extensionTestAppJarPath.get()}",
        "-Dio.opentelemetry.smoketest.springboot13.path=${legacySpringBootJarPath.get()}"
      )
    }
  }

  // Keep test/check as full-suite entry points; CI explicitly requests these disjoint partitions.
  register<Test>("testRepresentativeAppServers") {
    description = "Runs the representative app-server matrix shared by PRs and main."
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
  }
  register<Test>("testRemainingAppServers") {
    description = "Runs app-server cases outside the representative matrix."
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
  }

  register("smokeTest") {
    when {
      smokeTestSuite in appServerSuites -> {
        dependsOn(named("testRepresentativeAppServers"))
        if (!reducedAppServerTests) {
          dependsOn(named("testRemainingAppServers"))
        }
      }

      smokeTestSuite == null || smokeTestSuite in suites || smokeTestSuite == "other" || smokeTestSuite == "none" ->
        dependsOn(named("test"))

      else -> throw GradleException("Unknown smoke test suite: $smokeTestSuite")
    }
  }
}
