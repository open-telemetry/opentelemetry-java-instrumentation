plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("com.azure")
    module.set("azure-core")
    versions.set("[1.19.0,1.36.0)")
    assertInverse.set(true)
  }
}

sourceSets {
  main {
    val shadedDep = project(":instrumentation:azure-core:azure-core-1.19:library-instrumentation-shaded")
    output.dir(
      shadedDep.file("build/extracted/shadow"),
      "builtBy" to ":instrumentation:azure-core:azure-core-1.19:library-instrumentation-shaded:extractShadowJar"
    )
  }
}

dependencies {
  compileOnly(project(":instrumentation:azure-core:azure-core-1.19:library-instrumentation-shaded", configuration = "shadow"))

  library("com.azure:azure-core:1.19.0")

  // Ensure no cross interference
  testInstrumentation(project(":instrumentation:azure-core:azure-core-1.14:javaagent"))
  testInstrumentation(project(":instrumentation:azure-core:azure-core-1.36:javaagent"))
}

val latestDepTest = findProperty("testLatestDeps") as Boolean

testing {
  suites {
    // using a test suite to ensure that classes from library-instrumentation-shaded that were
    // extracted to the output directory are not available during tests
    val testAzure by registering(JvmTestSuite::class) {
      dependencies {
        val version = if (latestDepTest) "1.35.0" else "1.19.0"
        implementation("com.azure:azure-core:$version")
      }
    }
  }
}

tasks {
  check {
    dependsOn(testing.suites)
  }
}
