plugins {
  `kotlin-dsl`

  id("otel.java-conventions")
  id("otel.publish-conventions")
  id("com.gradle.plugin-publish")
}

group = "io.opentelemetry.instrumentation.gradle"

dependencies {
  implementation("com.google.guava:guava")
  implementation(project(":muzzle"))
  implementation("net.bytebuddy:byte-buddy-gradle-plugin")
  implementation("io.opentelemetry.javaagent:opentelemetry-javaagent-extension-api")
}

pluginBundle {
  website = "https://opentelemetry.io"
  vcsUrl = "https://github.com/open-telemetry/opentelemetry-java-instrumentation"
  tags = listOf("opentelemetry", "instrumentation")
}
