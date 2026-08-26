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
    // the agent matches methods of pekko classes by name, some of them are private and scala
    // mangles their names, run the tests against the scala 3 artifacts to catch a name that only
    // holds for scala 2
    register<JvmTestSuite>("scala3Test") {
      dependencies {
        implementation("org.scala-lang:scala3-library_3:3.3.6")
        implementation("org.apache.pekko:pekko-http_3:${baseVersion("1.0.0").orLatest()}")
        implementation("org.apache.pekko:pekko-stream_3:${baseVersion("1.0.1").orLatest()}")
      }
    }

    register<JvmTestSuite>("tapirTest") {
      dependencies {
        val scalaVersion = if (otelProps.testLatestDeps) "2.13" else "2.12"
        implementation("org.apache.pekko:pekko-http_$scalaVersion:${baseVersion("1.0.0").orLatest()}")
        implementation("org.apache.pekko:pekko-stream_$scalaVersion:${baseVersion("1.0.1").orLatest()}")
        implementation("com.softwaremill.sttp.tapir:tapir-pekko-http-server_$scalaVersion:${baseVersion("1.7.0").orLatest()}")
        if (otelProps.testLatestDeps) {
          implementation("org.apache.pekko:pekko-slf4j_2.13:latest.release")
          implementation("org.apache.pekko:pekko-actor_2.13:latest.release")
        }
      }
    }
  }
}

// the scala 3 suite runs the same tests as the scala 2 suite, against the _3 artifacts
sourceSets.named("scala3Test") {
  java.srcDir("src/test/java")
  resources.srcDir("src/test/resources")
  extensions.getByType(org.gradle.api.tasks.ScalaSourceDirectorySet::class.java).srcDir("src/test/scala")
}

// -target:jvm-1.8 is scala 2 syntax, the scala 3 compiler rejects it
tasks.named<ScalaCompile>("compileScala3TestScala") {
  scalaCompileOptions.additionalParameters =
    scalaCompileOptions.additionalParameters.orEmpty().filter { it != "-target:jvm-1.8" }
}

tasks {
  withType<Test>().configureEach {
    // required on jdk17
    jvmArgs("--add-exports=java.base/sun.security.util=ALL-UNNAMED")
    jvmArgs("-XX:+IgnoreUnrecognizedVMOptions")

    systemProperty("testLatestDeps", otelProps.testLatestDeps)
    systemProperty("collectMetadata", otelProps.collectMetadata)
  }

  check {
    dependsOn(testing.suites)
  }

  if (otelProps.denyUnsafe) {
    withType<Test>().configureEach {
      enabled = false
    }
  }
}

if (otelProps.testLatestDeps) {
  configurations {
    // pekko artifact name is different for regular and latest tests
    testImplementation {
      exclude("org.apache.pekko", "pekko-http_2.12")
      exclude("org.apache.pekko", "pekko-stream_2.12")
    }
  }
}
