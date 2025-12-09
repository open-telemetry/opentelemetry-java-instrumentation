import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
  id("otel.library-instrumentation")

  id("org.jetbrains.kotlin.jvm")
}

val ktorVersion = "3.0.0"

dependencies {
  library("io.ktor:ktor-client-core:$ktorVersion")
  library("io.ktor:ktor-server-core:$ktorVersion")

  api(project(":instrumentation:ktor:ktor-2-common:library"))
  implementation("io.opentelemetry:opentelemetry-extension-kotlin")

  compileOnly("org.jetbrains.kotlin:kotlin-stdlib-jdk8")

  testImplementation(project(":instrumentation:ktor:ktor-3.0:testing"))
  testImplementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")

  testLibrary("io.ktor:ktor-server-netty:$ktorVersion")
  testLibrary("io.ktor:ktor-client-cio:$ktorVersion")
}

kotlin {
  compilerOptions {
    jvmTarget.set(JvmTarget.JVM_1_8)
    @Suppress("deprecation")
    languageVersion.set(KotlinVersion.KOTLIN_1_8)
  }
}

tasks {
  withType<Test>().configureEach {
    systemProperty("collectMetadata", findProperty("collectMetadata")?.toString() ?: "false")
  }

  val testExperimental by registering(Test::class) {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath

    // this is used for enabling/disabling tests, library instrumentation doesn't use this flag
    jvmArgs("-Dotel.instrumentation.http.server.emit-experimental-telemetry=true")
    systemProperty("metadataConfig", "otel.instrumentation.http.server.emit-experimental-telemetry=true")
  }

  check {
    dependsOn(testExperimental)
  }
}
