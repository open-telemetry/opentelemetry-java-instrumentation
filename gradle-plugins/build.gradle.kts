import java.time.Duration

plugins {
  `kotlin-dsl`
  `maven-publish`

  id("com.gradle.plugin-publish")
  id("io.github.gradle-nexus.publish-plugin")
}

group = "io.opentelemetry.instrumentation"

apply(from = "../version.gradle.kts")

repositories {
  mavenCentral()
  gradlePluginPortal()
}

val bbGradlePlugin by configurations.creating
configurations.named("compileOnly") {
  extendsFrom(bbGradlePlugin)
}

dependencies {
  implementation("com.google.guava:guava:31.0.1-jre")
  // we need to use byte buddy variant that does not shade asm
  implementation("net.bytebuddy:byte-buddy-gradle-plugin:1.12.6") {
    exclude(group = "net.bytebuddy", module = "byte-buddy")
  }
  implementation("net.bytebuddy:byte-buddy-dep:1.12.3")

  implementation("org.eclipse.aether:aether-connector-basic:1.1.0")
  implementation("org.eclipse.aether:aether-transport-http:1.1.0")
  implementation("org.apache.maven:maven-aether-provider:3.3.9")

  implementation("gradle.plugin.com.github.johnrengelman:shadow:7.1.2")

  testImplementation("org.assertj:assertj-core:3.21.0")

  testImplementation(enforcedPlatform("org.junit:junit-bom:5.8.2"))
  testImplementation("org.junit.jupiter:junit-jupiter-api")
  testImplementation("org.junit.jupiter:junit-jupiter-params")
  testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
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

nexusPublishing {
  packageGroup.set("io.opentelemetry")

  repositories {
    sonatype {
      username.set(System.getenv("SONATYPE_USER"))
      password.set(System.getenv("SONATYPE_KEY"))
    }
  }

  connectTimeout.set(Duration.ofMinutes(5))
  clientTimeout.set(Duration.ofMinutes(5))
}

tasks {
  publishPlugins {
    enabled = !version.toString().contains("SNAPSHOT")
  }
}
