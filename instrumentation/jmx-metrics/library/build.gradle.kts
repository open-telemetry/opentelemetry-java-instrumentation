import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
  id("otel.library-instrumentation")
}

dependencies {
  implementation("org.snakeyaml:snakeyaml-engine")

  testImplementation(project(":testing-common"))
  testImplementation("org.testcontainers:testcontainers")

  testImplementation("org.testcontainers:junit-jupiter")
  testImplementation("com.linecorp.armeria:armeria-junit5:1.31.3")
  testImplementation("com.linecorp.armeria:armeria-junit5:1.31.3")
  testImplementation("com.linecorp.armeria:armeria-grpc:1.31.3")
  testImplementation("io.opentelemetry.proto:opentelemetry-proto:1.5.0-alpha")
}

tasks {
  test {
    // get packaged agent jar for testing
    val shadowTask = project(":javaagent").tasks.named<ShadowJar>("shadowJar").get()
    dependsOn(shadowTask)

    val testAppTask = project(":instrumentation:jmx-metrics:testing-webapp").tasks.named<War>("war")
    dependsOn(testAppTask)

    inputs.files(layout.files(shadowTask))
      .withPropertyName("javaagent")
      .withNormalizer(ClasspathNormalizer::class)

    doFirst {
      jvmArgs(
        "-Dio.opentelemetry.javaagent.path=${shadowTask.archiveFile.get()}",
        "-Dio.opentelemetry.testapp.path=${testAppTask.get().archiveFile.get().asFile.absolutePath}"
      )
    }
  }
}
