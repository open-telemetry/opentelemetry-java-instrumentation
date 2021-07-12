plugins {
  `kotlin-dsl`

  id("otel.java-conventions")
  id("otel.publish-conventions")
  id("com.gradle.plugin-publish")
}

group = "io.opentelemetry.instrumentation.gradle"

dependencies {
  compileOnly("com.google.guava:guava")
  compileOnly("net.bytebuddy:byte-buddy-gradle-plugin")

  implementation(platform("io.opentelemetry.instrumentation:opentelemetry-instrumentation-bom-alpha:1.4.0-alpha-SNAPSHOT"))
  implementation("io.opentelemetry.javaagent:opentelemetry-muzzle")
  implementation("io.opentelemetry.javaagent:opentelemetry-javaagent-extension-api")
}

pluginBundle {
  website = "https://opentelemetry.io"
  vcsUrl = "https://github.com/open-telemetry/opentelemetry-java-instrumentation"
  tags = listOf("opentelemetry", "instrumentation")
}
