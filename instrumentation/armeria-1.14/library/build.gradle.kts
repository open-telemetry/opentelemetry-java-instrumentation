plugins {
  id("otel.library-instrumentation")
  id("otel.nullaway-conventions")
}

dependencies {
  library("com.linecorp.armeria:armeria:1.14.0")

  testImplementation(project(":instrumentation:armeria-1.14:testing"))
}

tasks {
  val testStableSemconv by registering(Test::class) {
    jvmArgs("-Dotel.semconv-stability.opt-in=http")
  }

  withType<Test>().configureEach {
    systemProperty("testLatestDeps", findProperty("testLatestDeps") as Boolean)
  }

  check {
    dependsOn(testStableSemconv)
  }
}
