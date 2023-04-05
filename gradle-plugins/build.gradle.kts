import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.time.Duration

plugins {
  `kotlin-dsl`
  `maven-publish`
  signing

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

val byteBuddyVersion = "1.14.3"
val aetherVersion = "1.1.0"

dependencies {
  implementation("com.google.guava:guava:31.1-jre")
  // we need to use byte buddy variant that does not shade asm
  implementation("net.bytebuddy:byte-buddy-gradle-plugin:${byteBuddyVersion}") {
    exclude(group = "net.bytebuddy", module = "byte-buddy")
  }
  implementation("net.bytebuddy:byte-buddy-dep:${byteBuddyVersion}")

  implementation("org.eclipse.aether:aether-connector-basic:${aetherVersion}")
  implementation("org.eclipse.aether:aether-transport-http:${aetherVersion}")
  implementation("org.apache.maven:maven-aether-provider:3.3.9")

  implementation("gradle.plugin.com.github.johnrengelman:shadow:8.0.0")

  testImplementation("org.assertj:assertj-core:3.24.2")

  testImplementation(enforcedPlatform("org.junit:junit-bom:5.9.2"))
  testImplementation("org.junit.jupiter:junit-jupiter-api")
  testImplementation("org.junit.jupiter:junit-jupiter-params")
  testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
}

tasks {
  withType<Test>().configureEach {
    useJUnitPlatform()
  }

  withType<JavaCompile>().configureEach {
    with(options) {
      release.set(8)
    }
  }

  withType(KotlinCompile::class).configureEach {
    kotlinOptions {
      jvmTarget = "1.8"
    }
  }
}

gradlePlugin {
  website.set("https://opentelemetry.io")
  vcsUrl.set("https://github.com/open-telemetry/opentelemetry-java-instrumentation")
  plugins {
    get("io.opentelemetry.instrumentation.muzzle-generation").apply {
      displayName = "Muzzle safety net generation"
      description = "https://github.com/open-telemetry/opentelemetry-java-instrumentation/blob/main/docs/contributing/muzzle.md"
      tags.set(listOf("opentelemetry", "instrumentation", "java"))
    }
    get("io.opentelemetry.instrumentation.muzzle-check").apply {
      displayName = "Checks instrumented libraries against muzzle safety net"
      description = "https://github.com/open-telemetry/opentelemetry-java-instrumentation/blob/main/docs/contributing/muzzle.md"
      tags.set(listOf("opentelemetry", "instrumentation", "java"))
    }
  }
}

java {
  toolchain {
    languageVersion.set(JavaLanguageVersion.of(11))
  }
  withJavadocJar()
  withSourcesJar()
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

afterEvaluate {
  publishing {
    publications {
      named<MavenPublication>("pluginMaven") {
        pom {
          name.set("OpenTelemetry Instrumentation Gradle Plugins")
          description.set("Gradle plugins to assist developing OpenTelemetry instrumentation")
          url.set("https://github.com/open-telemetry/opentelemetry-java-instrumentation")

          licenses {
            license {
              name.set("The Apache License, Version 2.0")
              url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
            }
          }

          developers {
            developer {
              id.set("opentelemetry")
              name.set("OpenTelemetry")
              url.set("https://github.com/open-telemetry/opentelemetry-java-instrumentation/discussions")
            }
          }

          scm {
            connection.set("scm:git:git@github.com:open-telemetry/opentelemetry-java-instrumentation.git")
            developerConnection.set("scm:git:git@github.com:open-telemetry/opentelemetry-java-instrumentation.git")
            url.set("git@github.com:open-telemetry/opentelemetry-java-instrumentation.git")
          }
        }
      }
    }
  }
}

// Sign only if we have a key to do so
val signingKey: String? = System.getenv("GPG_PRIVATE_KEY")
signing {
  setRequired({
    // only require signing on CI and when a signing key is present
    System.getenv("CI") != null && signingKey != null
  })
  useInMemoryPgpKeys(signingKey, System.getenv("GPG_PASSWORD"))
}
