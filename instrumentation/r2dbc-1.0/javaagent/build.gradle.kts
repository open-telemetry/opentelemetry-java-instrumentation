plugins {
  id("otel.javaagent-instrumentation")
  id("otel.java-conventions")
  id("otel.nullaway-conventions")
}

muzzle {
  pass {
    group.set("io.r2dbc")
    module.set("r2dbc-spi")
    versions.set("[0.9.0.RELEASE,)")
    assertInverse.set(true)
    extraDependency("io.projectreactor:reactor-core:3.4.12")
  }
}

sourceSets {
  main {
    val shadedDep = project(":instrumentation:r2dbc-1.0:library-instrumentation-shaded")
    output.dir(
      shadedDep.file("build/extracted/shadow"),
      "builtBy" to ":instrumentation:r2dbc-1.0:library-instrumentation-shaded:extractShadowJar",
    )
  }
  test {
    java.srcDir("src/testSpiCompatibility/java")
    java.srcDir(project(":instrumentation:r2dbc-1.0:testing").file("src/testSpiCompatibility/java"))
  }
}

dependencies {
  library("io.r2dbc:r2dbc-spi:1.0.0.RELEASE")
  compileOnly(project(path = ":instrumentation:r2dbc-1.0:library-instrumentation-shaded", configuration = "shadow"))

  testImplementation("io.projectreactor:reactor-core:3.4.12")
  testImplementation(project(":instrumentation:r2dbc-1.0:testing"))
  testInstrumentation(project(":instrumentation:reactor:reactor-3.1:javaagent"))
}

testing {
  suites {
    register<JvmTestSuite>("testSpi09") {
      sources {
        java {
          setSrcDirs(
            listOf(
              "src/testSpi09/java",
              "src/testSpiCompatibility/java",
              project(":instrumentation:r2dbc-1.0:testing").file("src/testSpiCompatibility/java"),
            )
          )
        }
      }
      dependencies {
        implementation("io.r2dbc:r2dbc-h2:0.9.0.RELEASE")
      }
    }
  }
}

tasks {
  named<Test>("test") {
    usesService(gradle.sharedServices.registrations["testcontainersBuildService"].service)
  }

  val testPreviewSemconv = register<Test>("testPreviewSemconv") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    usesService(gradle.sharedServices.registrations["testcontainersBuildService"].service)
    jvmArgs("-Dotel.semconv-stability.preview=service.peer")
    systemProperty("metadataConfig", "otel.semconv-stability.preview=service.peer")
  }

  check {
    dependsOn(testing.suites)
    dependsOn(testPreviewSemconv)
  }
}
