plugins {
  `kotlin-dsl`
  `maven-publish`

  id("otel.java-conventions")
  id("com.gradle.plugin-publish")
}

group = "io.opentelemetry.javaagent"

repositories {
  mavenCentral()
}

dependencies {
  implementation("com.google.guava:guava:30.1.1-jre")
  implementation("net.bytebuddy:byte-buddy-gradle-plugin:1.11.2")

  implementation(project(":instrumentation-api"))
  implementation(project(":javaagent-extension-api"))
  implementation(project(":muzzle"))

  implementation("org.eclipse.aether:aether-connector-basic:1.1.0")
  implementation("org.eclipse.aether:aether-transport-http:1.1.0")
  implementation("org.apache.maven:maven-aether-provider:3.3.9")

  testImplementation("org.assertj:assertj-core:3.19.0")

  testImplementation(enforcedPlatform("org.junit:junit-bom:5.7.2"))
  testImplementation("org.junit.jupiter:junit-jupiter-api")
  testImplementation("org.junit.jupiter:junit-jupiter-params")
  testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")

  testImplementation("io.opentelemetry.javaagent:opentelemetry-javaagent-instrumentation-api:1.6.0-alpha-SNAPSHOT")
}

tasks.withType<Test>().configureEach {
  useJUnitPlatform()
}

pluginBundle {
  website = "https://opentelemetry.io"
  vcsUrl = "https://github.com/open-telemetry/opentelemetry-java-instrumentation"
  tags = listOf("opentelemetry", "instrumentation", "java")
}

gradlePlugin {
  plugins {
    get("io.opentelemetry.instrumentation.muzzle-generation").apply {
      displayName = "Muzzle safety net generation"
      description = "https://github.com/open-telemetry/opentelemetry-java-instrumentation/blob/main/docs/contributing/muzzle.md"
    }
    get("io.opentelemetry.instrumentation.muzzle-check").apply {
      displayName = "Checks instrumented libraries against muzzle safety net"
      description = "https://github.com/open-telemetry/opentelemetry-java-instrumentation/blob/main/docs/contributing/muzzle.md"
    }
  }
}

tasks {
  publishPlugins {
    enabled = !version.toString().contains("SNAPSHOT")
  }
}
