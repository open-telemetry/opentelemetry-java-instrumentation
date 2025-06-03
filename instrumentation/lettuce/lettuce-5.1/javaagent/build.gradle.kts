plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("io.lettuce")
    module.set("lettuce-core")
    versions.set("[5.1.0.RELEASE,)")
    assertInverse.set(true)
  }
}

dependencies {
  library("io.lettuce:lettuce-core:5.1.0.RELEASE")

  implementation(project(":instrumentation:lettuce:lettuce-5.1:library"))

  testImplementation(project(":instrumentation:lettuce:lettuce-5.1:testing"))

  // Only 5.2+ will have command arguments in the db.statement tag.
  testLibrary("io.lettuce:lettuce-core:5.2.0.RELEASE")
  testInstrumentation(project(":instrumentation:reactor:reactor-3.1:javaagent"))
}

tasks {
  withType<Test>().configureEach {
    val testLatestDeps = findProperty("testLatestDeps") as Boolean
    systemProperty("testLatestDeps", testLatestDeps)
    usesService(gradle.sharedServices.registrations["testcontainersBuildService"].service)

    dependencies {
      if (testLatestDeps) {
        // This is only needed for 6.7.0, can be removed when 6.7.1 is released.
        // See https://github.com/redis/lettuce/issues/3317
        testLibrary("io.micrometer:micrometer-core:1.5.0")
      }
    }
  }

  val testStableSemconv by registering(Test::class) {
    jvmArgs("-Dotel.semconv-stability.opt-in=database")
  }

  check {
    dependsOn(testStableSemconv)
  }
}
