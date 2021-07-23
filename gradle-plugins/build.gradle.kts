import java.time.Duration

plugins {
  `kotlin-dsl`
  `maven-publish`

  id("com.gradle.plugin-publish")
  id("io.github.gradle-nexus.publish-plugin")
}

group = "io.opentelemetry.instrumentation"
version = "0.2.0-SNAPSHOT"

repositories {
  mavenCentral()
  mavenLocal()
  maven {
    url = uri("https://oss.sonatype.org/content/repositories/snapshots")
  }
}

dependencies {
  implementation("com.google.guava:guava:30.1.1-jre")
  implementation("net.bytebuddy:byte-buddy-gradle-plugin:1.11.2")
  implementation("io.opentelemetry.instrumentation:opentelemetry-instrumentation-api:1.5.0-alpha-SNAPSHOT")
  implementation("io.opentelemetry.javaagent:opentelemetry-muzzle:1.5.0-alpha-SNAPSHOT")
  implementation("io.opentelemetry.javaagent:opentelemetry-javaagent-extension-api:1.5.0-alpha-SNAPSHOT")

  implementation("org.eclipse.aether:aether-connector-basic:1.1.0")
  implementation("org.eclipse.aether:aether-transport-http:1.1.0")
  implementation("org.apache.maven:maven-aether-provider:3.3.9")

  testImplementation("org.assertj:assertj-core:3.19.0")

  testImplementation(enforcedPlatform("org.junit:junit-bom:5.7.2"))
  testImplementation("org.junit.jupiter:junit-jupiter-api")
  testImplementation("org.junit.jupiter:junit-jupiter-params")
  testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")

  testImplementation("io.opentelemetry.javaagent:opentelemetry-javaagent-instrumentation-api:1.4.0-alpha-SNAPSHOT")
}

pluginBundle {
  website = "https://opentelemetry.io"
  vcsUrl = "https://github.com/open-telemetry/opentelemetry-java-instrumentation"
  tags = listOf("opentelemetry", "instrumentation")
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
