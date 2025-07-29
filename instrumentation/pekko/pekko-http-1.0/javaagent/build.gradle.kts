plugins {
  id("otel.javaagent-instrumentation")
  id("otel.scala-conventions")
}

muzzle {
  pass {
    group.set("org.apache.pekko")
    module.set("pekko-http_2.12")
    versions.set("[1.0,)")
    assertInverse.set(true)
    extraDependency("org.apache.pekko:pekko-stream_2.12:1.0.1")
    excludeInstrumentationName("tapir-pekko-http-server")
  }
  pass {
    group.set("org.apache.pekko")
    module.set("pekko-http_2.13")
    versions.set("[1.0,)")
    assertInverse.set(true)
    extraDependency("org.apache.pekko:pekko-stream_2.13:1.0.1")
    excludeInstrumentationName("tapir-pekko-http-server")
  }
  pass {
    group.set("org.apache.pekko")
    module.set("pekko-http_3")
    versions.set("[1.0,)")
    assertInverse.set(true)
    extraDependency("org.apache.pekko:pekko-stream_3:1.0.1")
    excludeInstrumentationName("tapir-pekko-http-server")
  }
  pass {
    group.set("com.softwaremill.sttp.tapir")
    module.set("tapir-pekko-http-server_2.12")
    versions.set("[1.7,)")
    assertInverse.set(true)
    excludeInstrumentationName("pekko-http-server")
  }
  pass {
    group.set("com.softwaremill.sttp.tapir")
    module.set("tapir-pekko-http-server_2.13")
    versions.set("[1.7,)")
    assertInverse.set(true)
    excludeInstrumentationName("pekko-http-server")
  }
  pass {
    group.set("com.softwaremill.sttp.tapir")
    module.set("tapir-pekko-http-server_3")
    versions.set("[1.7,)")
    assertInverse.set(true)
    excludeInstrumentationName("pekko-http-server")
  }
}

dependencies {
  library("org.apache.pekko:pekko-http_2.12:1.0.0")
  library("org.apache.pekko:pekko-stream_2.12:1.0.1")
  compileOnly("com.softwaremill.sttp.tapir:tapir-pekko-http-server_2.12:1.7.0")

  testInstrumentation(project(":instrumentation:pekko:pekko-actor-1.0:javaagent"))
  testInstrumentation(project(":instrumentation:executors:javaagent"))

  latestDepTestLibrary("org.apache.pekko:pekko-http_2.13:latest.release")
  latestDepTestLibrary("org.apache.pekko:pekko-stream_2.13:latest.release")
}

testing {
  suites {
    val tapirTest by registering(JvmTestSuite::class) {
      dependencies {
        // this only exists to make Intellij happy since it doesn't (currently at least) understand our
        // inclusion of this artifact inside :testing-common
        compileOnly(project.dependencies.project(":testing:armeria-shaded-for-testing", configuration = "shadow"))

        if (findProperty("testLatestDeps") as Boolean) {
          implementation("com.typesafe.akka:akka-http_2.13:latest.release")
          implementation("com.typesafe.akka:akka-stream_2.13:latest.release")
          implementation("com.softwaremill.sttp.tapir:tapir-pekko-http-server_2.13:latest.release")
        } else {
          implementation("org.apache.pekko:pekko-http_2.12:1.0.0")
          implementation("org.apache.pekko:pekko-stream_2.12:1.0.1")
          implementation("com.softwaremill.sttp.tapir:tapir-pekko-http-server_2.12:1.7.0")
        }
      }
    }
  }
}

tasks {
  withType<Test>().configureEach {
    // required on jdk17
    jvmArgs("--add-exports=java.base/sun.security.util=ALL-UNNAMED")
    jvmArgs("-XX:+IgnoreUnrecognizedVMOptions")

    systemProperty("testLatestDeps", findProperty("testLatestDeps") as Boolean)
    systemProperty("collectMetadata", findProperty("collectMetadata")?.toString() ?: "false")
  }

  check {
    dependsOn(testing.suites)
  }
}

if (findProperty("testLatestDeps") as Boolean) {
  configurations {
    // pekko artifact name is different for regular and latest tests
    testImplementation {
      exclude("org.apache.pekko", "pekko-http_2.12")
      exclude("org.apache.pekko", "pekko-stream_2.12")
    }
  }
}
