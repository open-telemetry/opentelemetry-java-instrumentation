plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("com.azure")
    module.set("azure-core")
    versions.set("[1.36.0,1.53.0)")
    assertInverse.set(true)
    // this module bridges an explicitly supplied application parent context, so it references the
    // application's io.opentelemetry.context.{Context,Scope} and only applies when the application
    // uses the OpenTelemetry API itself; it is verified separately below
    excludeInstrumentationName("azure-core-1.36-context")
  }

  pass {
    name.set("Application using the OpenTelemetry API")
    group.set("com.azure")
    module.set("azure-core")
    versions.set("[1.36.0,1.53.0)")
    assertInverse.set(true)
    extraDependency("io.opentelemetry:opentelemetry-api:1.27.0")
  }
}

sourceSets {
  main {
    val shadedDep = project(":instrumentation:azure-core:azure-core-1.36:library-instrumentation-shaded")
    output.dir(
      shadedDep.file("build/extracted/shadow"),
      "builtBy" to ":instrumentation:azure-core:azure-core-1.36:library-instrumentation-shaded:extractShadowJar"
    )
  }
}

dependencies {
  compileOnly(project(":instrumentation:azure-core:azure-core-1.36:library-instrumentation-shaded", configuration = "shadow"))

  // needed to bridge an explicitly supplied application parent context (the unshaded
  // "application.io.opentelemetry.*" types) to the agent context inside our advice
  compileOnly(project(":opentelemetry-api-shaded-for-instrumenting", configuration = "shadow"))

  library("com.azure:azure-core:1.36.0")

  // Ensure no cross interference
  testInstrumentation(project(":instrumentation:azure-core:azure-core-1.14:javaagent"))
  testInstrumentation(project(":instrumentation:azure-core:azure-core-1.19:javaagent"))
  testInstrumentation(project(":instrumentation:azure-core:azure-core-1.53:javaagent"))
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
    register<JvmTestSuite>("testAzure") {
      dependencies {
        implementation("com.azure:azure-core:${baseVersion("1.36.0").orLatest("1.52.0")}")
        implementation("com.azure:azure-core-test:${baseVersion("1.14.1").orLatest("1.26.2")}")
      }
    }
  }
}

tasks {
  check {
    dependsOn(testing.suites)
  }
}
