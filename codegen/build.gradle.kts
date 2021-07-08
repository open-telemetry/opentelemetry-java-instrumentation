plugins {
  `kotlin-dsl`

  id("com.gradle.plugin-publish")

  id("otel.java-conventions")
  id("otel.publish-conventions")

}

group = "io.opentelemetry.instrumentation.gradle"

val versions: Map<String, String> by project

dependencies {
  // Only used during compilation by bytebuddy plugin
  compileOnly("com.google.guava:guava")
  implementation(project(":muzzle"))
  implementation("net.bytebuddy:byte-buddy-gradle-plugin:${versions["net.bytebuddy"]}")
  implementation("io.opentelemetry.javaagent:opentelemetry-javaagent-extension-api")
}

pluginBundle {
  website = "https://opentelemetry.io"
  vcsUrl = "https://github.com/open-telemetry/opentelemetry-java-instrumentation"
  tags = listOf("opentelemetry", "instrumentation")
}
