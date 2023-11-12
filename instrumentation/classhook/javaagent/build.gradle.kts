import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
  id("otel.javaagent-instrumentation")
}

dependencies {
  testImplementation("org.testcontainers:testcontainers:1.19.1")
  testImplementation("com.squareup.okhttp3:okhttp:4.12.0")
}

tasks {
  test {
    testLogging.showStandardStreams = true

    val shadowTask = project(":javaagent").tasks.named<ShadowJar>("shadowJar").get()

    doFirst {
      jvmArgs(
        "-Dio.opentelemetry.smoketest.agent.shadowJar.path=${shadowTask.archiveFile.get()}")
    }
  }
}
