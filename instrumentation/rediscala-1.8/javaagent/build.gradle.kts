plugins {
  id("otel.javaagent-instrumentation")
  id("otel.scala-conventions")
}

muzzle {
  pass {
    group.set("com.github.etaty")
    module.set("rediscala_2.11")
    versions.set("[1.5.0,)")
    assertInverse.set(true)
  }

  pass {
    group.set("com.github.etaty")
    module.set("rediscala_2.12")
    versions.set("[1.8.0,)")
    assertInverse.set(true)
  }

  pass {
    group.set("com.github.etaty")
    module.set("rediscala_2.13")
    versions.set("[1.9.0,)")
    assertInverse.set(true)
  }

  pass {
    group.set("com.github.Ma27")
    module.set("rediscala_2.11")
    versions.set("[1.8.1,)")
    assertInverse.set(true)
  }

  pass {
    group.set("com.github.Ma27")
    module.set("rediscala_2.12")
    versions.set("[1.8.1,)")
    assertInverse.set(true)
  }

  pass {
    group.set("com.github.Ma27")
    module.set("rediscala_2.13")
    versions.set("[1.9.0,)")
    assertInverse.set(true)
  }

  pass {
    group.set("io.github.rediscala")
    module.set("rediscala_2.13")
    versions.set("[1.10.0,)")
    assertInverse.set(true)
  }
}

dependencies {
  library("com.github.etaty:rediscala_2.11:1.8.0")

  latestDepTestLibrary("io.github.rediscala:rediscala_2.13:latest.release")
}

tasks {
  withType<Test>().configureEach {
    systemProperty("testLatestDeps", findProperty("testLatestDeps") as Boolean)
    usesService(gradle.sharedServices.registrations["testcontainersBuildService"].service)

    systemProperty("collectMetadata", findProperty("collectMetadata")?.toString() ?: "false")
  }

  val testStableSemconv by registering(Test::class) {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath

    jvmArgs("-Dotel.semconv-stability.opt-in=database")
    systemProperty("metadataConfig", "otel.semconv-stability.opt-in=database")
  }

  check {
    dependsOn(testStableSemconv)
  }

  if (findProperty("denyUnsafe") as Boolean) {
    withType<Test>().configureEach {
      enabled = false
    }
  }
}

if (findProperty("testLatestDeps") as Boolean) {
  configurations {
    // rediscala artifact name is different for regular and latest tests
    testImplementation {
      exclude("com.github.etaty", "rediscala_2.11")
    }
  }
}
