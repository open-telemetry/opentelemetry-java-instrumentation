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

testing {
  suites {
    register<JvmTestSuite>("unitTests") {
      dependencies {
        implementation(project())
        implementation(project(":instrumentation:thrift-0.13:testing"))
      }
    }
  }
}

tasks {
  check {
    dependsOn(testing.suites)
  }
}

tasks.test {
  systemProperty("testLatestDeps", otelProps.testLatestDeps)
}
