import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
  id("otel.java-conventions")
}

description = "e2e benchmark"

dependencies {
  implementation("org.junit.jupiter:junit-jupiter-api:5.3.1")
  implementation("org.slf4j:slf4j-simple:1.6.1")
  implementation("org.testcontainers:testcontainers:1.15.3")
}

tasks {
  test {
    dependsOn(":javaagent:shadowJar")
    maxParallelForks = 2

    doFirst {
      jvmArgs("-Dio.opentelemetry.smoketest.agent.shadowJar.path=${project(":javaagent").tasks.getByName<ShadowJar>("shadowJar").archivePath}")
    }
  }
}
