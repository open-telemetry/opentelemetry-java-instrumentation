import io.opentelemetry.instrumentation.gradle.executions
import io.opentelemetry.instrumentation.gradle.telemetryConfig

plugins {
  id("otel.javaagent-instrumentation")
  id("otel.java-conventions")
  id("otel.nullaway-conventions")
}

muzzle {
  pass {
    group.set("io.r2dbc")
    module.set("r2dbc-spi")
    versions.set("[0.9.0.RELEASE,)")
    assertInverse.set(true)
    extraDependency("io.projectreactor:reactor-core:3.4.12")
  }
}

sourceSets {
  main {
    val shadedDep = project(":instrumentation:r2dbc-1.0:library-instrumentation-shaded")
    output.dir(
      shadedDep.file("build/extracted/shadow"),
      "builtBy" to ":instrumentation:r2dbc-1.0:library-instrumentation-shaded:extractShadowJar",
    )
  }
}

dependencies {
  library("io.r2dbc:r2dbc-spi:0.9.0.RELEASE")
  compileOnly(project(path = ":instrumentation:r2dbc-1.0:library-instrumentation-shaded", configuration = "shadow"))

  testImplementation(project(":instrumentation:r2dbc-1.0:testing"))
  testInstrumentation(project(":instrumentation:reactor:reactor-3.1:javaagent"))
}

testing {
  suites {
    named<JvmTestSuite>("test") {
      executions {
        register("WithPeerServiceMapping") {
          telemetryConfig("otel.instrumentation.common.peer-service-mapping", "127.0.0.1=test-peer-service,localhost=test-peer-service,192.0.2.1=test-peer-service")
        }
        register("PreviewSemconvWithPeerServiceMapping") {
          telemetryConfig("otel.semconv-stability.preview", "service.peer")
          telemetryConfig("otel.instrumentation.common.peer-service-mapping", "127.0.0.1=test-peer-service,localhost=test-peer-service,192.0.2.1=test-peer-service")
        }
      }
    }
  }
}

tasks {
  withType<Test>().configureEach {
    usesService(gradle.sharedServices.registrations["testcontainersBuildService"].service)
  }

}
