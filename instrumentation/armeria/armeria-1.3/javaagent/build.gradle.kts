plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("com.linecorp.armeria")
    module.set("armeria")
    versions.set("[1.3.0,)")
    assertInverse.set(true)
  }
}

dependencies {
  implementation(project(":instrumentation:armeria:armeria-1.3:library"))
  testInstrumentation(project(":instrumentation:netty:netty-4.1:javaagent"))

  library("com.linecorp.armeria:armeria:1.3.0")
  testLibrary("com.linecorp.armeria:armeria-junit5:1.3.0")

  testImplementation(project(":instrumentation:armeria:armeria-1.3:testing"))

  // needed for latest dep tests
  testCompileOnly("com.google.errorprone:error_prone_annotations")
}

testing {
  suites {
    val testArmeria19 by registering(JvmTestSuite::class) {
      sources {
        java {
          setSrcDirs(listOf("src/test/java", "src/testArmeria19/java"))
        }
      }
      dependencies {
        implementation("com.linecorp.armeria:armeria:1.9.2")
        implementation("com.linecorp.armeria:armeria-junit5:1.9.2")
        implementation(project(":instrumentation:armeria:armeria-1.3:testing"))
        compileOnly("com.google.errorprone:error_prone_annotations")
      }
    }
  }
}

tasks {
  withType<Test>().configureEach {
    systemProperty("testLatestDeps", findProperty("testLatestDeps"))
    systemProperty("collectMetadata", findProperty("collectMetadata"))
  }

  val testStableSemconv by registering(Test::class) {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    jvmArgs("-Dotel.semconv-stability.opt-in=service.peer")
    systemProperty("metadataConfig", "otel.semconv-stability.opt-in=service.peer")
  }

  check {
    dependsOn(testing.suites, testStableSemconv)
  }

  if (findProperty("denyUnsafe") == "true") {
    withType<Test>().configureEach {
      enabled = false
    }
  }
}
