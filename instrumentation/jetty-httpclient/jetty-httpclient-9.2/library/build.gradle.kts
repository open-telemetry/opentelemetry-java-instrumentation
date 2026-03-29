plugins {
  id("otel.library-instrumentation")
}

// Jetty client 9.2 is the best starting point, HttpClient.send() is stable there
val jettyVers_base9 = "9.2.0.v20140526"

dependencies {
  library("org.eclipse.jetty:jetty-client:$jettyVers_base9")

  testImplementation(project(":instrumentation:jetty-httpclient:jetty-httpclient-9.2:testing"))

  latestDepTestLibrary("org.eclipse.jetty:jetty-client:9.+") // documented limitation
}

testing {
  suites {
    // regression test for https://github.com/open-telemetry/opentelemetry-java-instrumentation/issues/16405
    val testDemandedContentListener by registering(JvmTestSuite::class) {
      sources {
        java {
          setSrcDirs(listOf("src/test/java"))
        }
      }
      dependencies {
        implementation(project())
        implementation(project(":instrumentation:jetty-httpclient::jetty-httpclient-9.2:testing"))
        val jettyVersion = if (findProperty("testLatestDeps") == "true") "9.4.43.v20210629" else "9.4.24.v20191120"
        implementation("org.eclipse.jetty:jetty-client:$jettyVersion")
      }
    }
  }
}

tasks {
  val testStableSemconv by registering(Test::class) {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    jvmArgs("-Dotel.semconv-stability.opt-in=service.peer")
  }

  check {
    dependsOn(testing.suites, testStableSemconv)
  }
}
