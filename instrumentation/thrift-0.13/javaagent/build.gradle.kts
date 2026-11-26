import io.opentelemetry.instrumentation.gradle.executions
import io.opentelemetry.instrumentation.gradle.unitTests

plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("org.apache.thrift")
    module.set("libthrift")
    versions.set("[0.13.0,)")
    assertInverse.set(true)
  }
}

dependencies {
  library("org.apache.thrift:libthrift:0.13.0")
  implementation(project(":instrumentation:thrift-0.13:library"))
  testImplementation(project(":instrumentation:thrift-0.13:testing"))
}

testing.suites {
  unitTests {
    dependencies {
      implementation(project(":instrumentation:thrift-0.13:testing"))
    }
  }

  executions("test") {
    configureDefault {
      systemProperty("testLatestDeps", otelProps.testLatestDeps)
    }
  }
}
