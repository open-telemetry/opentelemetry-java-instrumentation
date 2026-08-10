
plugins {
  id("otel.library-instrumentation")
}

dependencies {
  implementation("org.snakeyaml:snakeyaml-engine")

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

    // the base agent only contains the agent machinery and the internal instrumentations that it
    // requires, the JMX instrumentation is added on top of it. Using the full agent would capture
    // telemetry from the target systems that is unrelated to JMX metrics.
    val baseAgentTask = project(":javaagent").tasks.named<Jar>("baseJavaagentJar")
    val jmxAgentTask = project(":instrumentation:jmx-metrics:javaagent").tasks.named<Jar>("shadowJar")
    val testAppTask = project(":instrumentation:jmx-metrics:testing-apps:testing-webapp").tasks.named<War>("war")
    val camelTestAppTask = project(":instrumentation:jmx-metrics:testing-apps:camel-testing-app").tasks.named<Jar>("camelTestAppJar")

    dependsOn(baseAgentTask)
    dependsOn(jmxAgentTask)
    dependsOn(testAppTask)
    dependsOn(camelTestAppTask)

    val agentJar = baseAgentTask.flatMap { it.archiveFile }
    val jmxInstrumentationJar = jmxAgentTask.flatMap { it.archiveFile }
    val testAppWar = testAppTask.flatMap { it.archiveFile }
    val camelTestAppJar = camelTestAppTask.flatMap { it.archiveFile }

    inputs.file(agentJar)
      .withPropertyName("javaagent")
      .withNormalizer(ClasspathNormalizer::class)
    inputs.file(jmxInstrumentationJar)
      .withPropertyName("jmxInstrumentation")
      .withNormalizer(ClasspathNormalizer::class)
    inputs.file(testAppWar)
      .withPropertyName("testWebApp")
      .withNormalizer(ClasspathNormalizer::class)
    inputs.file(camelTestAppJar)
      .withPropertyName("camelTestApp")
      .withNormalizer(ClasspathNormalizer::class)

    val registryDir = layout.projectDirectory.dir("../model").asFile.absolutePath
    inputs.dir(registryDir).withPathSensitivity(PathSensitivity.RELATIVE)

    jvmArgumentProviders += CommandLineArgumentProvider {
      listOf(
        "-Dio.opentelemetry.javaagent.path=${agentJar.get().asFile.absolutePath}",
        "-Dio.opentelemetry.javaagent.jmx.path=${jmxInstrumentationJar.get().asFile.absolutePath}",
        "-Dio.opentelemetry.testapp.path=${testAppWar.get().asFile.absolutePath}",
        "-Dio.opentelemetry.registry.path=$registryDir",
        "-Dio.opentelemetry.cameltestapp.path=${camelTestAppJar.get().asFile.absolutePath}",
      )
    }
  }
}
