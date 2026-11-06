import io.opentelemetry.instrumentation.gradle.executions

plugins {
  id("otel.javaagent-instrumentation")
  id("otel.scala-conventions")
}

muzzle {
  pass {
    group.set("org.apache.pekko")
    module.set("pekko-actor_2.12")
    versions.set("[1.0,)")
    assertInverse.set(true)
    excludeInstrumentationModule("io.opentelemetry.javaagent.instrumentation.pekkoactor.v1_0.typed.PekkoActorTypedInstrumentationModule")
  }
  pass {
    group.set("org.apache.pekko")
    module.set("pekko-actor_2.13")
    versions.set("[1.0,)")
    assertInverse.set(true)
    excludeInstrumentationModule("io.opentelemetry.javaagent.instrumentation.pekkoactor.v1_0.typed.PekkoActorTypedInstrumentationModule")
  }
  pass {
    group.set("org.apache.pekko")
    module.set("pekko-actor_3")
    versions.set("[1.0,)")
    assertInverse.set(true)
    excludeInstrumentationModule("io.opentelemetry.javaagent.instrumentation.pekkoactor.v1_0.typed.PekkoActorTypedInstrumentationModule")
  }
  pass {
    group.set("org.apache.pekko")
    module.set("pekko-actor-typed_2.12")
    versions.set("[1.0,)")
    assertInverse.set(true)
    excludeInstrumentationModule("io.opentelemetry.javaagent.instrumentation.pekkoactor.v1_0.PekkoActorInstrumentationModule")
  }
  pass {
    group.set("org.apache.pekko")
    module.set("pekko-actor-typed_2.13")
    versions.set("[1.0,)")
    assertInverse.set(true)
    excludeInstrumentationModule("io.opentelemetry.javaagent.instrumentation.pekkoactor.v1_0.PekkoActorInstrumentationModule")
  }
  pass {
    group.set("org.apache.pekko")
    module.set("pekko-actor-typed_3")
    versions.set("[1.0,)")
    assertInverse.set(true)
    excludeInstrumentationModule("io.opentelemetry.javaagent.instrumentation.pekkoactor.v1_0.PekkoActorInstrumentationModule")
  }
}

dependencies {
  bootstrap(project(":instrumentation:executors:bootstrap"))

  library("org.apache.pekko:pekko-actor_2.12:1.0.1")
  library("org.apache.pekko:pekko-actor-typed_2.12:1.0.1")

  latestDepTestLibrary("org.apache.pekko:pekko-actor_2.13:latest.release")
  latestDepTestLibrary("org.apache.pekko:pekko-actor-typed_2.13:latest.release")

  testImplementation(project(":instrumentation:executors:testing"))
}

testing.suites {
  // the agent matches methods of pekko classes by name, some of them are private and scala
  // mangles their names, run the tests against the scala 3 artifacts to catch a name that only
  // holds for scala 2
  register<JvmTestSuite>("scala3Test") {
    sources {
      java.srcDir("src/test/java")
      resources.srcDir("src/test/resources")
      extensions.getByType(org.gradle.api.tasks.ScalaSourceDirectorySet::class.java).srcDir("src/test/scala")
    }

    dependencies {
      implementation("org.scala-lang:scala3-library_3:3.3.6")
      implementation("org.apache.pekko:pekko-actor_3:${baseVersion("1.0.1").orLatest()}")
      implementation("org.apache.pekko:pekko-actor-typed_3:${baseVersion("1.0.1").orLatest()}")
      implementation(project(":instrumentation:executors:testing"))
    }
  }

  // pekko 2.x has only milestone releases so far, which latest dep testing skips, run the tests
  // against them explicitly, pekko 2.x requires java 17 and has no scala 2.12 artifacts
  register<JvmTestSuite>("pekko2Test") {
    sources {
      java.srcDir("src/test/java")
      resources.srcDir("src/test/resources")
      extensions.getByType(org.gradle.api.tasks.ScalaSourceDirectorySet::class.java).srcDir("src/test/scala")
    }

    dependencies {
      implementation("org.apache.pekko:pekko-actor_2.13:${baseVersion("2.0.0-M4").orLatest("2.+")}")
      implementation("org.apache.pekko:pekko-actor-typed_2.13:${baseVersion("2.0.0-M4").orLatest("2.+")}")
      implementation(project(":instrumentation:executors:testing"))
    }
  }

  register<JvmTestSuite>("pekko2Scala3Test") {
    sources {
      java.srcDir("src/test/java")
      resources.srcDir("src/test/resources")
      extensions.getByType(org.gradle.api.tasks.ScalaSourceDirectorySet::class.java).srcDir("src/test/scala")
    }
    dependencies {
      implementation("org.scala-lang:scala3-library_3:3.3.8")
      implementation("org.apache.pekko:pekko-actor_3:${baseVersion("2.0.0-M4").orLatest("2.+")}")
      implementation("org.apache.pekko:pekko-actor-typed_3:${baseVersion("2.0.0-M4").orLatest("2.+")}")
      implementation(project(":instrumentation:executors:testing"))
    }

    tasks.named<ScalaCompile>("compilePekko2Scala3TestScala") {
      scalaCompileOptions.additionalParameters =
        scalaCompileOptions.additionalParameters.orEmpty().filter { it != "-target:jvm-1.8" } +
        "-release:17"
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
}

tasks {
  check {
    dependsOn(testing.suites)
  }
}

if (otelProps.testLatestDeps) {
  configurations {
    // pekko artifact name is different for regular and latest tests
    testImplementation {
      exclude("org.apache.pekko", "pekko-actor_2.12")
      exclude("org.apache.pekko", "pekko-actor-typed_2.12")
    }
  }
}

if (otelProps.denyUnsafe) {
  tasks.withType<Test>().configureEach {
    enabled = false
  }
}
