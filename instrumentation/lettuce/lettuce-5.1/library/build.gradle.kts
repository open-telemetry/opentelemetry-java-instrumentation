plugins {
  id("otel.library-instrumentation")
  id("otel.nullaway-conventions")
}

dependencies {
  library("io.lettuce:lettuce-core:5.1.0.RELEASE")

  implementation(project(":instrumentation:lettuce:lettuce-common:library"))

  testImplementation(project(":instrumentation:lettuce:lettuce-5.1:testing"))
  testImplementation(project(":instrumentation:reactor:reactor-3.1:library"))
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
