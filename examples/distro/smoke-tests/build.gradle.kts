import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
  id("otel.java-conventions")
}

dependencies {
  testImplementation("org.testcontainers:testcontainers:1.21.3")
  testImplementation("com.fasterxml.jackson.core:jackson-databind:2.19.1")
  testImplementation("com.google.protobuf:protobuf-java-util:4.31.1")
  testImplementation("com.squareup.okhttp3:okhttp:4.12.0")
  testImplementation("io.opentelemetry.proto:opentelemetry-proto:1.7.0-alpha")
  testImplementation("io.opentelemetry:opentelemetry-api")

  testImplementation("ch.qos.logback:logback-classic:1.5.18")
}

tasks.test {
  useJUnitPlatform()

  testLogging.showStandardStreams = true

  val shadowTask = project(":agent").tasks.named<ShadowJar>("shadowJar").get()
  dependsOn(shadowTask)
  inputs.files(layout.files(shadowTask))

  doFirst {
    jvmArgs("-Dio.opentelemetry.smoketest.agent.shadowJar.path=${shadowTask.archiveFile.get().asFile.absolutePath}")
  }
} 