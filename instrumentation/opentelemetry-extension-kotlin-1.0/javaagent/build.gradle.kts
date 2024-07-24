import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
  id("org.jetbrains.kotlin.jvm")
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("io.opentelemetry")
    module.set("opentelemetry-extension-kotlin")
    versions.set("[0.17.0,)")
    assertInverse.set(true)
    skip("0.13.0") // has a bad dependency on non-alpha api-metric 0.13.0
    extraDependency("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.3.0")
  }
}

dependencies {
  compileOnly("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
  library("io.opentelemetry:opentelemetry-extension-kotlin")
  // see the comment in opentelemetry-api-1.0.gradle for more details
  compileOnly(project(":opentelemetry-api-shaded-for-instrumenting", configuration = "shadow"))

  implementation(project(":instrumentation:opentelemetry-api:opentelemetry-api-1.0:javaagent"))

  testImplementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
  testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.3.0")
}

if (!(findProperty("testLatestDeps") as Boolean)) {
  // run tests against an early version of opentelemetry-extension-kotlin, latest dep tests will use
  // the current version
  configurations.configureEach {
    if (!name.contains("muzzle")) {
      resolutionStrategy {
        eachDependency {
          if (requested.group == "io.opentelemetry" && requested.name == "opentelemetry-extension-kotlin") {
            useVersion("1.0.0")
          }
        }
      }
    }
  }
}

kotlin {
  compilerOptions {
    jvmTarget.set(JvmTarget.JVM_1_8)
  }
}
