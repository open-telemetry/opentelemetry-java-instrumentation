import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  id("org.jetbrains.kotlin.jvm")
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("org.jetbrains.kotlinx")
    module.set("ktor-server-core")
    versions.set("[3.0.0-beta-1,)")
  }
}

val ktorVersion = "3.0.0-beta-1"

dependencies {
  library("io.ktor:ktor-client-core:$ktorVersion")
  library("io.ktor:ktor-server-core:$ktorVersion")

  implementation(project(":instrumentation:ktor:ktor-3.0:library"))

  compileOnly("org.jetbrains.kotlin:kotlin-stdlib-jdk8")

  testInstrumentation(project(":instrumentation:netty:netty-4.1:javaagent"))

  testImplementation(project(":instrumentation:ktor:ktor-3.0:testing"))
  testImplementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
  testImplementation("io.opentelemetry:opentelemetry-extension-kotlin")

  testLibrary("io.ktor:ktor-server-netty:$ktorVersion")
  testLibrary("io.ktor:ktor-client-cio:$ktorVersion")
}

tasks {
  withType(KotlinCompile::class).configureEach {
    kotlinOptions {
      jvmTarget = "1.8"
    }
  }
}
