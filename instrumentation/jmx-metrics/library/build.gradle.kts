
plugins {
  id("otel.library-instrumentation")
}

dependencies {
  implementation("org.snakeyaml:snakeyaml-engine")

  testImplementation("io.opentelemetry.javaagent:opentelemetry-testing-common")
  testImplementation("org.testcontainers:testcontainers")

  testImplementation("org.testcontainers:testcontainers-junit-jupiter")
  testImplementation("com.linecorp.armeria:armeria-junit5:1.31.3")
  testImplementation("com.linecorp.armeria:armeria-junit5:1.31.3")
  testImplementation("com.linecorp.armeria:armeria-grpc:1.31.3")
  testImplementation("io.opentelemetry.proto:opentelemetry-proto:1.5.0-alpha")
}

tasks {
  test {
    val shadowTask = project(":javaagent").tasks.named<Jar>("shadowJar")
    val testAppTask = project(":instrumentation:jmx-metrics:testing-webapp").tasks.named<War>("war")

    dependsOn(shadowTask)
    dependsOn(testAppTask)

    val agentJar = shadowTask.flatMap { it.archiveFile }
    val testAppWar = testAppTask.flatMap { it.archiveFile }

    inputs.file(agentJar)
      .withPropertyName("javaagent")
      .withNormalizer(ClasspathNormalizer::class)
    inputs.file(testAppWar)
      .withPropertyName("testWebApp")
      .withNormalizer(ClasspathNormalizer::class)

    jvmArgumentProviders += CommandLineArgumentProvider {
      listOf(
        "-Dio.opentelemetry.javaagent.path=${agentJar.get().asFile.absolutePath}",
        "-Dio.opentelemetry.testapp.path=${testAppWar.get().asFile.absolutePath}",
      )
    }
  }
}
