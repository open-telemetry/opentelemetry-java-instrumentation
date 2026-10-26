plugins {
  id("otel.javaagent-instrumentation")
  id("otel.nullaway-conventions")
}

muzzle {
  pass {
    group.set("org.springframework")
    module.set("spring-core")
    versions.set("[2.0,]")
    assertInverse.set(true)
  }
}

dependencies {
  bootstrap(project(":instrumentation:executors:bootstrap"))

  library("org.springframework:spring-core:2.0")
}

// 3.0 introduces submit() methods
// 4.0 introduces submitListenable() methods
testing {
  suites {
    val version4Test by registering(JvmTestSuite::class) {
      dependencies {
        implementation(sourceSets.test.get().output)
        implementation("org.springframework:spring-core:${baseVersion("4.0.0.RELEASE").orLatest()}")
      }
    }
  }
}

tasks {
  check {
    dependsOn(testing.suites)
  }
}

// spring 6 requires java 17
if (otelProps.testLatestDeps) {
  otelJava {
    minJavaVersionSupported.set(JavaVersion.VERSION_17)
  }
}
