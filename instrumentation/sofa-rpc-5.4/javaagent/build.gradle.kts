import io.opentelemetry.instrumentation.gradle.executions
import io.opentelemetry.instrumentation.gradle.telemetryConfig

plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("com.alipay.sofa")
    module.set("sofa-rpc-all")
    versions.set("[5.4.0,)")
    assertInverse.set(true)
  }
}

dependencies {
  implementation(project(":instrumentation:sofa-rpc-5.4:library"))

  library("com.alipay.sofa:sofa-rpc-all:5.4.0")
}

testing {
  suites {
    register<JvmTestSuite>("testSofaRpc") {
      dependencies {
        implementation(project(":instrumentation:sofa-rpc-5.4:testing"))
        implementation("com.alipay.sofa:sofa-rpc-all:${baseVersion("5.4.0").orLatest()}")
      }
      executions {
        register("WithServiceMapping") {
          telemetryConfig(
            "otel.instrumentation.common.server-service-mapping",
            "127.0.0.1=test-server-service,localhost=test-server-service,192.0.2.1=test-server-service",
          )
        }
      }
    }
  }
}

tasks.withType<Test>().configureEach {
  systemProperty("testLatestDeps", otelProps.testLatestDeps)
  jvmArgs("-XX:+IgnoreUnrecognizedVMOptions")
  // required on jdk17
  jvmArgs("--add-opens=java.base/java.lang=ALL-UNNAMED")
}

val exceptionSignalLogsSuites = testing.suites.withType(JvmTestSuite::class).map { suite ->
  tasks.register<Test>("${suite.name}ExceptionSignalLogs") {
    testClassesDirs = suite.sources.output.classesDirs
    classpath = suite.sources.runtimeClasspath

    jvmArgs("-Dotel.semconv.exception.signal.preview=logs")
    systemProperty("metadataConfig", "otel.semconv.exception.signal.preview=logs")
  }
}

tasks {
  check {
    dependsOn(testing.suites, exceptionSignalLogsSuites)
  }
}

configurations.named("testSofaRpcRuntimeClasspath") {
  resolutionStrategy {
    // requires old logback (and therefore also old slf4j)
    force("ch.qos.logback:logback-classic:1.2.13")
    force("ch.qos.logback:logback-core:1.2.13")
    force("org.slf4j:slf4j-api:1.7.21")
  }
}

if (otelProps.denyUnsafe) {
  tasks.withType<Test>().configureEach {
    enabled = false
  }
}
