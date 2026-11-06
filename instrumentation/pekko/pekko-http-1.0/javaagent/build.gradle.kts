import io.opentelemetry.instrumentation.gradle.executions

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

  // later versions are tested in pekko2Test suite
  latestDepTestLibrary("org.apache.pekko:pekko-http_2.13:1.+") // documented limitation
  latestDepTestLibrary("org.apache.pekko:pekko-stream_2.13:1.+") // documented limitation
}

testing.suites {
  // pekko-http 2.x removed the server binding methods the tests used, so each suite needs the
  // binding shim for its pekko-http line.
  named<JvmTestSuite>("test") {
    sources {
      extensions.getByType(ScalaSourceDirectorySet::class.java).srcDir("src/pekko1TestShim/scala")
    }
  }

  register<JvmTestSuite>("pekko11Test") {
    sources {
      java.srcDir("src/test/java")
      resources.srcDir("src/test/resources")
      extensions.getByType(ScalaSourceDirectorySet::class.java).srcDir("src/test/scala")
      extensions.getByType(ScalaSourceDirectorySet::class.java).srcDir("src/pekko1TestShim/scala")
    }

    dependencies {
      implementation("org.apache.pekko:pekko-http_2.13:1.1.0")
      implementation("org.apache.pekko:pekko-stream_2.13:1.1.0")
    }
  }

  // the agent matches methods of pekko classes by name, some of them are private and scala
  // mangles their names, run the tests against the scala 3 artifacts to catch a name that only
  // holds for scala 2
  register<JvmTestSuite>("scala3Test") {
    sources {
      java.srcDir("src/test/java")
      resources.srcDir("src/test/resources")
      extensions.getByType(ScalaSourceDirectorySet::class.java).srcDir("src/test/scala")
      extensions.getByType(ScalaSourceDirectorySet::class.java).srcDir("src/pekko1TestShim/scala")
    }

    dependencies {
      implementation("org.scala-lang:scala3-library_3:3.3.6")
      implementation("org.apache.pekko:pekko-http_3:${baseVersion("1.0.0").orLatest("1.+")}")
      implementation("org.apache.pekko:pekko-stream_3:${baseVersion("1.0.1").orLatest("1.+")}")
    }

    tasks.named<ScalaCompile>("compileScala3TestScala") {
      scalaCompileOptions.additionalParameters =
        scalaCompileOptions.additionalParameters.orEmpty().filter { it != "-target:jvm-1.8" } +
        "-release:8"
    }
  }

  // pekko 2.x has only milestone releases so far, which latest dep testing skips, run the tests
  // against them explicitly, pekko 2.x requires java 17 and has no scala 2.12 artifacts
  register<JvmTestSuite>("pekko2Test") {
    sources {
      java.srcDir("src/test/java")
      resources.srcDir("src/test/resources")
      extensions.getByType(ScalaSourceDirectorySet::class.java).srcDir("src/test/scala")
      extensions.getByType(ScalaSourceDirectorySet::class.java).srcDir("src/pekko2TestShim/scala")
    }

    dependencies {
      implementation("org.apache.pekko:pekko-http_2.13:${baseVersion("2.0.0-M2").orLatest()}")
      implementation("org.apache.pekko:pekko-stream_2.13:${baseVersion("2.0.0-M4").orLatest()}")
    }
  }

  register<JvmTestSuite>("pekko2Scala3Test") {
    sources {
      java.srcDir("src/test/java")
      resources.srcDir("src/test/resources")
      extensions.getByType(ScalaSourceDirectorySet::class.java).srcDir("src/test/scala")
      extensions.getByType(ScalaSourceDirectorySet::class.java).srcDir("src/pekko2TestShim/scala")
    }

    dependencies {
      implementation("org.scala-lang:scala3-library_3:3.3.8")
      implementation("org.apache.pekko:pekko-http_3:${baseVersion("2.0.0-M2").orLatest()}")
      implementation("org.apache.pekko:pekko-stream_3:${baseVersion("2.0.0-M4").orLatest()}")
    }

    tasks.named<ScalaCompile>("compilePekko2Scala3TestScala") {
      scalaCompileOptions.additionalParameters =
        scalaCompileOptions.additionalParameters.orEmpty().filter { it != "-target:jvm-1.8" } +
        "-release:17"
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

  val testJavaVersion = otelProps.testJavaVersion ?: JavaVersion.current()
  executions("pekko2Test", "pekko2Scala3Test") {
    configureEach {
      if (!testJavaVersion.isCompatibleWith(JavaVersion.VERSION_17)) {
        enabled = false
      }
    }
  }

  executions("test") {
    register("NewServerAt") {
      systemProperty("testNewServerAt", true)
    }
  }

  executions("pekko11Test") {
    register("NoSpanSuppression") {
      jvmArgs("-Dotel.instrumentation.common.span-suppression-strategy=none")

      filter {
        includeTestsMatching("*PekkoHttpServerInstrumentationTestAsync.successfulGetRequest")
      }
    }
  }
}

tasks {
  withType<Test>().configureEach {
    // required on jdk17
    jvmArgs("--add-exports=java.base/sun.security.util=ALL-UNNAMED")
    jvmArgs("-XX:+IgnoreUnrecognizedVMOptions")
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
