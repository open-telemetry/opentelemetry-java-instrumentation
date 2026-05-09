plugins {
  id("otel.java-conventions")
}

dependencies {
  testImplementation("org.testcontainers:testcontainers:2.0.5")
  testImplementation("com.fasterxml.jackson.core:jackson-databind:2.21.3")
  testImplementation("com.google.protobuf:protobuf-java-util:4.34.1")
  testImplementation("com.squareup.okhttp3:okhttp:5.3.2")
  testImplementation("io.opentelemetry.proto:opentelemetry-proto:1.10.0-alpha")
  testImplementation("io.opentelemetry:opentelemetry-api")
  testImplementation("org.assertj:assertj-core:3.27.7")

  testImplementation("ch.qos.logback:logback-classic:1.5.32")
}

tasks.test {
  testLogging.showStandardStreams = true

  val agentJar = project(":agent").tasks.named<Jar>("shadowJar").flatMap { it.archiveFile }
  inputs.file(agentJar)

  doFirst {
    jvmArgs("-Dio.opentelemetry.smoketest.agent.shadowJar.path=${agentJar.get().asFile}")
  }
}
