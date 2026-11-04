import io.opentelemetry.instrumentation.gradle.executions
import io.opentelemetry.instrumentation.gradle.telemetryConfig

plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("tech.powerjob")
    module.set("powerjob-worker")
    versions.set("[4.0.0,)")
    assertInverse.set(true)
    extraDependency("tech.powerjob:powerjob-official-processors:1.1.0")
  }
}

dependencies {
  library("tech.powerjob:powerjob-worker:4.0.0")
  library("tech.powerjob:powerjob-official-processors:1.1.0")
}

testing.suites.named<JvmTestSuite>("test") {
  executions {
    configureEach {
      // required on jdk17
      jvmArgs("--add-opens=java.base/java.lang=ALL-UNNAMED")
      jvmArgs("-XX:+IgnoreUnrecognizedVMOptions")
    }
    register("Experimental") {
      telemetryConfig("otel.instrumentation.powerjob.experimental-span-attributes", "true")
    }
  }
}
