plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("com.azure")
    module.set("azure-core")
    versions.set("[1.14.0,1.19.0)")
    assertInverse.set(true)
  }
}

sourceSets {
  main {
    val shadedDep = project(":instrumentation:azure-core:azure-core-1.14:library-instrumentation-shaded")
    output.dir(
      shadedDep.file("build/extracted/shadow"),
      "builtBy" to ":instrumentation:azure-core:azure-core-1.14:library-instrumentation-shaded:extractShadowJar"
    )
  }
}

dependencies {
  compileOnly(project(":instrumentation:azure-core:azure-core-1.14:library-instrumentation-shaded", configuration = "shadow"))

  library("com.azure:azure-core:1.14.0")

  // Ensure no cross interference
  testInstrumentation(project(":instrumentation:azure-core:azure-core-1.19:javaagent"))
  testInstrumentation(project(":instrumentation:azure-core:azure-core-1.36:javaagent"))
}

val latestDepTest = findProperty("testLatestDeps") as Boolean

testing {
  suites {
    // using a test suite to ensure that classes from library-instrumentation-shaded that were
    // extracted to the output directory are not available during tests
    val testAzure by registering(JvmTestSuite::class) {
      dependencies {
        if (latestDepTest) {
          implementation("com.azure:azure-core:1.18.0") // see azure-core-1.19 module
        } else {
          implementation("com.azure:azure-core:1.14.0")
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
