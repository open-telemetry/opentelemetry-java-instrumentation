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
    excludeInstrumentationModule("io.opentelemetry.javaagent.instrumentation.pekkohttp.v1_0.server.tapir.TapirPekkoHttpServerRouteInstrumentationModule")
  }
  pass {
    group.set("org.apache.pekko")
    module.set("pekko-http_2.13")
    versions.set("[1.0,)")
    assertInverse.set(true)
    extraDependency("org.apache.pekko:pekko-stream_2.13:1.0.1")
    excludeInstrumentationModule("io.opentelemetry.javaagent.instrumentation.pekkohttp.v1_0.server.tapir.TapirPekkoHttpServerRouteInstrumentationModule")
  }
  pass {
    group.set("org.apache.pekko")
    module.set("pekko-http_3")
    versions.set("[1.0,)")
    assertInverse.set(true)
    extraDependency("org.apache.pekko:pekko-stream_3:1.0.1")
    excludeInstrumentationModule("io.opentelemetry.javaagent.instrumentation.pekkohttp.v1_0.server.tapir.TapirPekkoHttpServerRouteInstrumentationModule")
  }
  pass {
    group.set("com.softwaremill.sttp.tapir")
    module.set("tapir-pekko-http-server_2.12")
    versions.set("[1.7,)")
    assertInverse.set(true)
    excludeInstrumentationModule("io.opentelemetry.javaagent.instrumentation.pekkohttp.v1_0.client.PekkoHttpClientInstrumentationModule")
    excludeInstrumentationModule("io.opentelemetry.javaagent.instrumentation.pekkohttp.v1_0.server.PekkoHttpServerInstrumentationModule")
    excludeInstrumentationModule("io.opentelemetry.javaagent.instrumentation.pekkohttp.v1_0.server.route.PekkoHttpServerRouteInstrumentationModule")
  }
  pass {
    group.set("com.softwaremill.sttp.tapir")
    module.set("tapir-pekko-http-server_2.13")
    versions.set("[1.7,)")
    assertInverse.set(true)
    excludeInstrumentationModule("io.opentelemetry.javaagent.instrumentation.pekkohttp.v1_0.client.PekkoHttpClientInstrumentationModule")
    excludeInstrumentationModule("io.opentelemetry.javaagent.instrumentation.pekkohttp.v1_0.server.PekkoHttpServerInstrumentationModule")
    excludeInstrumentationModule("io.opentelemetry.javaagent.instrumentation.pekkohttp.v1_0.server.route.PekkoHttpServerRouteInstrumentationModule")
  }
  pass {
    group.set("com.softwaremill.sttp.tapir")
    module.set("tapir-pekko-http-server_3")
    versions.set("[1.7,)")
    assertInverse.set(true)
    excludeInstrumentationModule("io.opentelemetry.javaagent.instrumentation.pekkohttp.v1_0.client.PekkoHttpClientInstrumentationModule")
    excludeInstrumentationModule("io.opentelemetry.javaagent.instrumentation.pekkohttp.v1_0.server.PekkoHttpServerInstrumentationModule")
    excludeInstrumentationModule("io.opentelemetry.javaagent.instrumentation.pekkohttp.v1_0.server.route.PekkoHttpServerRouteInstrumentationModule")
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

    // pekko 2.x has only milestone releases so far, which latest dep testing skips, run the tests
    // against them explicitly, pekko 2.x requires java 17 and has no scala 2.12 artifacts
    register<JvmTestSuite>("pekko2Test") {
      dependencies {
        implementation("org.apache.pekko:pekko-http_2.13:${baseVersion("2.0.0-M2").orLatest("2.+")}")
        implementation("org.apache.pekko:pekko-stream_2.13:${baseVersion("2.0.0-M4").orLatest("2.+")}")
      }
    }

    register<JvmTestSuite>("pekko2Scala3Test") {
      dependencies {
        implementation("org.scala-lang:scala3-library_3:3.3.8")
        implementation("org.apache.pekko:pekko-http_3:${baseVersion("2.0.0-M2").orLatest("2.+")}")
        implementation("org.apache.pekko:pekko-stream_3:${baseVersion("2.0.0-M4").orLatest("2.+")}")
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

// the scala 3 and pekko 2 suites run the same tests as the scala 2 suite, against other artifacts
listOf("scala3Test", "pekko2Test", "pekko2Scala3Test").forEach {
  sourceSets.named(it) {
    java.srcDir("src/test/java")
    resources.srcDir("src/test/resources")
    extensions.getByType(org.gradle.api.tasks.ScalaSourceDirectorySet::class.java).srcDir("src/test/scala")
  }
}

// pekko-http 2.x removed the server binding methods the tests used, each suite gets the binding
// shim for the pekko-http line it runs against
listOf("test", "scala3Test").forEach {
  sourceSets.named(it) {
    extensions.getByType(org.gradle.api.tasks.ScalaSourceDirectorySet::class.java).srcDir("src/pekko1TestShim/scala")
  }
}
listOf("pekko2Test", "pekko2Scala3Test").forEach {
  sourceSets.named(it) {
    extensions.getByType(org.gradle.api.tasks.ScalaSourceDirectorySet::class.java).srcDir("src/pekko2TestShim/scala")
  }
}

// -target:jvm-1.8 is scala 2 syntax that the scala 3 compiler rejects, -release is how scala 3
// targets an older jvm, without it the tests are compiled for whichever jdk runs the build and
// can not be loaded when the tests run on java 8
tasks.named<ScalaCompile>("compileScala3TestScala") {
  scalaCompileOptions.additionalParameters =
    scalaCompileOptions.additionalParameters.orEmpty().filter { it != "-target:jvm-1.8" } +
    "-release:8"
}

tasks.named<ScalaCompile>("compilePekko2Scala3TestScala") {
  scalaCompileOptions.additionalParameters =
    scalaCompileOptions.additionalParameters.orEmpty().filter { it != "-target:jvm-1.8" } +
    "-release:17"
}

tasks {
  val testJavaVersion = otelProps.testJavaVersion ?: JavaVersion.current()
  if (!testJavaVersion.isCompatibleWith(JavaVersion.VERSION_17)) {
    named<Test>("pekko2Test") {
      enabled = false
    }
    named<Test>("pekko2Scala3Test") {
      enabled = false
    }
  }

  withType<Test>().configureEach {
    // required on jdk17
    jvmArgs("--add-exports=java.base/sun.security.util=ALL-UNNAMED")
    jvmArgs("-XX:+IgnoreUnrecognizedVMOptions")

    systemProperty("testLatestDeps", otelProps.testLatestDeps)
    systemProperty("collectMetadata", otelProps.collectMetadata)
  }

  // the test suite binds servers with the bindAndHandle* methods that pekko-http 2.x removed, run
  // it again binding with newServerAt, the api that replaced them
  val testNewServerAt = register<Test>("testNewServerAt") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath

    systemProperty("testNewServerAt", true)
  }

  check {
    dependsOn(testing.suites, testNewServerAt)
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
