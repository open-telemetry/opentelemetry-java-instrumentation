import io.opentelemetry.instrumentation.gradle.executions
import io.opentelemetry.instrumentation.gradle.telemetryConfig

plugins {
  id("otel.javaagent-instrumentation")
  id("otel.nullaway-conventions")
}

muzzle {
  pass {
    group.set("org.springframework")
    module.set("spring-context")
    versions.set("[3.1.0.RELEASE,]")
    assertInverse.set(true)
  }
}

dependencies {
  bootstrap(project(":instrumentation:spring:spring-scheduling-3.1:bootstrap"))

  library("org.springframework:spring-context:3.1.0.RELEASE")
}

testing.suites.named<JvmTestSuite>("test") {
  executions {
    configureEach {
      // required on jdk17
      jvmArgs("--add-opens=java.base/java.lang=ALL-UNNAMED")
      jvmArgs("-XX:+IgnoreUnrecognizedVMOptions")
      systemProperty("testLatestDeps", otelProps.testLatestDeps)
    }
    register("Experimental") {
      telemetryConfig("otel.instrumentation.spring-scheduling.experimental-span-attributes", "true")
    }
  }
}

// spring 6 requires java 17
if (otelProps.testLatestDeps) {
  otelJava {
    minJavaVersionSupported.set(JavaVersion.VERSION_17)
  }
}
