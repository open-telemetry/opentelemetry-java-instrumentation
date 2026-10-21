import io.opentelemetry.instrumentation.gradle.executions
import io.opentelemetry.instrumentation.gradle.telemetryConfig

plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("io.ratpack")
    module.set("ratpack-core")
    versions.set("[1.4.0,)")
    excludeInstrumentationModule("io.opentelemetry.javaagent.instrumentation.ratpack.v1_4.httpclient.RatpackHttpClientInstrumentationModule")
  }
  pass {
    // instrumentation-docs:ignore - verification only, the directive above is the range we document
    name.set("Ratpack HTTP client instrumentation")
    group.set("io.ratpack")
    module.set("ratpack-core")
    versions.set("[1.7.0,)")
    assertInverse.set(true)
    excludeInstrumentationModule("io.opentelemetry.javaagent.instrumentation.ratpack.v1_4.RatpackInstrumentationModule")
    excludeInstrumentationName("netty-4.1")
  }
}

dependencies {
  library("io.ratpack:ratpack-core:1.4.0")
  compileOnly("io.ratpack:ratpack-core:1.7.0")

  implementation(project(":instrumentation:netty:netty-4.1:javaagent"))
  implementation(project(":instrumentation:netty:netty-4.1:library"))
  implementation(project(":instrumentation:ratpack:ratpack-1.7:library"))

  testImplementation(project(":instrumentation:ratpack:ratpack-1.4:testing"))

  // 1.4.0 has a bug which makes tests flaky
  // (https://github.com/ratpack/ratpack/commit/dde536ac138a76c34df03a0642c88d64edde688e)
  testLibrary("io.ratpack:ratpack-test:1.4.1")

  if (JavaVersion.current().isCompatibleWith(JavaVersion.VERSION_11)) {
    testImplementation("com.sun.activation:jakarta.activation:1.2.2")
  }

  latestDepTestLibrary("io.ratpack:ratpack-core:1.6.+") // see test suite below
  latestDepTestLibrary("io.ratpack:ratpack-test:1.6.+") // see test suite below
}

if (!otelProps.testLatestDeps) {
  configurations.testRuntimeClasspath.get().resolutionStrategy.force("com.google.guava:guava:19.0")
}

listOf("testCompileClasspath", "testRuntimeClasspath").forEach {
  configurations.named(it) {
    resolutionStrategy {
      eachDependency {
        if (requested.group == "io.netty") {
          useVersion("4.1.31.Final")
        }
      }
    }
  }
}

testing.suites.register<JvmTestSuite>("version17Test") {
  dependencies {
    implementation(project(":instrumentation:ratpack:ratpack-1.4:testing"))
    implementation(project(":instrumentation:ratpack:ratpack-1.7:library"))
    val ratpackVersion = baseVersion("1.7.0").orLatest()
    implementation("io.ratpack:ratpack-core:$ratpackVersion")
    implementation("io.ratpack:ratpack-test:$ratpackVersion")
  }
}

tasks {
  withType<Test>().configureEach {
    systemProperty("testLatestDeps", otelProps.testLatestDeps)
    systemProperty("otel.instrumentation.common.controller-telemetry.enabled", true)
    systemProperty("metadataConfig", "otel.instrumentation.common.controller-telemetry.enabled=true")
  }

  if (otelProps.denyUnsafe) {
    withType<Test>().configureEach {
      enabled = false
    }
  }

  testing.suites.withType<JvmTestSuite>().configureEach {
    val ratpack14Test = name == "test"
    executions {
      configureEach {
        if (ratpack14Test) {
          systemProperty("ratpack14Test", true)
        }
      }
      register("PreviewSemconv") {
        telemetryConfig("otel.semconv-stability.preview", "service.peer")
      }
      register("ControllerTelemetryDisabled") {
        filter {
          includeTestsMatching(
            "io.opentelemetry.javaagent.instrumentation.ratpack.v1_4.server.RatpackErrorContextTest"
          )
        }
        systemProperty("otel.instrumentation.common.controller-telemetry.enabled", false)
        systemProperties.remove("metadataConfig")
      }
    }
  }
}
