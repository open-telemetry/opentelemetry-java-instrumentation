plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("com.azure")
    module.set("azure-core")
    versions.set("[1.53.0,)")
    assertInverse.set(true)
    // our advice helper bridges an explicitly supplied application parent context to the agent
    // context, so it references io.opentelemetry.context.{Context,Scope}
    extraDependency("io.opentelemetry:opentelemetry-api:1.27.0")
  }
}

sourceSets {
  main {
    val shadedDep = project(":instrumentation:azure-core:azure-core-1.53:library-instrumentation-shaded")
    output.dir(
      shadedDep.file("build/extracted/shadow"),
      "builtBy" to ":instrumentation:azure-core:azure-core-1.53:library-instrumentation-shaded:extractShadowJar"
    )
  }
}

dependencies {
  compileOnly(project(":instrumentation:azure-core:azure-core-1.53:library-instrumentation-shaded", configuration = "shadow"))

  // needed to bridge an explicitly supplied application parent context (the unshaded
  // "application.io.opentelemetry.*" types) to the agent context inside our advice
  compileOnly(project(":opentelemetry-api-shaded-for-instrumenting", configuration = "shadow"))

  library("com.azure:azure-core:1.53.0")

  // Ensure no cross interference
  testInstrumentation(project(":instrumentation:azure-core:azure-core-1.14:javaagent"))
  testInstrumentation(project(":instrumentation:azure-core:azure-core-1.19:javaagent"))
  testInstrumentation(project(":instrumentation:azure-core:azure-core-1.36:javaagent"))
}

tasks {
  withType<Test>().configureEach {
    systemProperty("testLatestDeps", otelProps.testLatestDeps)
  }
}

testing {
  suites {
    // using a test suite to ensure that classes from library-instrumentation-shaded that were
    // extracted to the output directory are not available during tests
    val testAzure by registering(JvmTestSuite::class) {
      dependencies {
        if (otelProps.testLatestDeps) {
          implementation("com.azure:azure-core:latest.release")
          implementation("com.azure:azure-core-test:latest.release")
        } else {
          implementation("com.azure:azure-core:1.53.0")
          implementation("com.azure:azure-core-test:1.26.2")
        }
      }
    }
  }
}

tasks {
  check {
    dependsOn(testing.suites)
  }
}
