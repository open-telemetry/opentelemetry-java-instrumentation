import java.time.Duration

import java.time.Duration as JavaDuration

plugins {
  id("otel.library-instrumentation")
}

val baseAgent = configurations.create("baseAgent") {
  isCanBeConsumed = false
  isCanBeResolved = true
}

val jmxAgent = configurations.create("jmxAgent") {
  isCanBeConsumed = false
  isCanBeResolved = true
}

val testWebApp = configurations.create("testWebApp") {
  isCanBeConsumed = false
  isCanBeResolved = true
}

val camelTestApp = configurations.create("camelTestApp") {
  isCanBeConsumed = false
  isCanBeResolved = true
}

dependencies {
  implementation("org.snakeyaml:snakeyaml-engine")

  baseAgent(project(":javaagent", configuration = "baseJar"))
  jmxAgent(project(":instrumentation:jmx-metrics:javaagent", configuration = "shadow"))
  testWebApp(project(":instrumentation:jmx-metrics:testing-apps:testing-webapp", configuration = "testWebApp"))
  camelTestApp(project(":instrumentation:jmx-metrics:testing-apps:camel-testing-app", configuration = "camelTestApp"))

  testImplementation("io.opentelemetry.javaagent:opentelemetry-testing-common")
  testImplementation("org.testcontainers:testcontainers")

  testImplementation("org.testcontainers:testcontainers-junit-jupiter")
  testImplementation("com.linecorp.armeria:armeria-junit5:1.31.3")
  testImplementation("com.linecorp.armeria:armeria-grpc:1.31.3")
  testImplementation("io.opentelemetry.proto:opentelemetry-proto:1.5.0-alpha")
  testImplementation("io.github.netmikey.logunit:logunit-jul")

  testImplementation(platform("io.grpc:grpc-bom:1.82.1"))
  testImplementation("io.grpc:grpc-netty-shaded")
}

tasks {
  test {
    usesService(gradle.sharedServices.registrations["testcontainersBuildService"].service)
    timeout.set(Duration.ofMinutes(30))

    // This suite starts multiple real target systems, including Trino.
    timeout.set(JavaDuration.ofMinutes(20))

    // the base agent only contains the agent machinery and the internal instrumentations that it
    // requires, the JMX instrumentation is added on top of it. Using the full agent would capture
    // telemetry from the target systems that is unrelated to JMX metrics.
    inputs.files(baseAgent)
      .withPropertyName("javaagent")
      .withNormalizer(ClasspathNormalizer::class)
    inputs.files(jmxAgent)
      .withPropertyName("jmxInstrumentation")
      .withNormalizer(ClasspathNormalizer::class)
    inputs.files(testWebApp)
      .withPropertyName("testWebApp")
      .withNormalizer(ClasspathNormalizer::class)
    inputs.files(camelTestApp)
      .withPropertyName("camelTestApp")
      .withNormalizer(ClasspathNormalizer::class)

    val agentJarPath = baseAgent.elements.map { it.single().asFile.absolutePath }
    val jmxInstrumentationJarPath = jmxAgent.elements.map { it.single().asFile.absolutePath }
    val testAppWarPath = testWebApp.elements.map { it.single().asFile.absolutePath }
    val camelTestAppJarPath = camelTestApp.elements.map { it.single().asFile.absolutePath }
    val registryDir = layout.projectDirectory.dir("../model").asFile.absolutePath
    inputs.dir(registryDir).withPathSensitivity(PathSensitivity.RELATIVE)

    jvmArgumentProviders += CommandLineArgumentProvider {
      listOf(
        "-Dio.opentelemetry.javaagent.path=${agentJarPath.get()}",
        "-Dio.opentelemetry.javaagent.jmx.path=${jmxInstrumentationJarPath.get()}",
        "-Dio.opentelemetry.testapp.path=${testAppWarPath.get()}",
        "-Dio.opentelemetry.registry.path=$registryDir",
        "-Dio.opentelemetry.cameltestapp.path=${camelTestAppJarPath.get()}",
      )
    }
  }
}
