plugins {
  `kotlin-dsl`
  `maven-publish`

  id("com.gradle.plugin-publish")
}

group = "io.opentelemetry.instrumentation.gradle"
version = "0.1.0"

dependencies {
  implementation("net.bytebuddy:byte-buddy-gradle-plugin:1.11.2")
}

pluginBundle {
  website = "https://opentelemetry.io"
  vcsUrl = "https://github.com/open-telemetry/opentelemetry-java-instrumentation"
  tags = listOf("opentelemetry", "instrumentation")
}
