plugins {
  `kotlin-dsl`
  `maven-publish`

  id("com.gradle.plugin-publish")
}

group = "io.opentelemetry.instrumentation.gradle"
version = "0.1.0"

repositories {
  mavenCentral()
  mavenLocal()
}

dependencies {
  implementation("com.google.guava:guava:30.1.1-jre")
  implementation("net.bytebuddy:byte-buddy-gradle-plugin:1.11.2")
  implementation("io.opentelemetry.javaagent:opentelemetry-muzzle:1.4.0-alpha-SNAPSHOT")
  implementation("io.opentelemetry.javaagent:opentelemetry-javaagent-extension-api:1.4.0-alpha-SNAPSHOT")
}

pluginBundle {
  website = "https://opentelemetry.io"
  vcsUrl = "https://github.com/open-telemetry/opentelemetry-java-instrumentation"
  tags = listOf("opentelemetry", "instrumentation")
}
