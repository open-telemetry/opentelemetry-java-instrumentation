plugins {
  id("otel.javaagent-instrumentation")
}
muzzle {
  pass {
    group.set("commons-httpclient")
    module.set("commons-httpclient")
    versions.set("[2.0,4.0)")
    assertInverse.set(true)
  }
}

dependencies {
  library("commons-httpclient:commons-httpclient:2.0")

  latestDepTestLibrary("commons-httpclient:commons-httpclient:3.+") // see apache-httpclient-4.0 module

  testInstrumentation(project(":instrumentation:apache-httpclient:apache-httpclient-4.0:javaagent"))
  testInstrumentation(project(":instrumentation:apache-httpclient:apache-httpclient-5.0:javaagent"))
}

tasks {
  withType<Test>().configureEach {
    systemProperty("testLatestDeps", findProperty("testLatestDeps") as Boolean)
    systemProperty("collectMetadata", findProperty("collectMetadata")?.toString() ?: "false")
  }

  val testStableSemconv by registering(Test::class) {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    jvmArgs("-Dotel.semconv-stability.opt-in=service.peer")
    systemProperty("metadataConfig", "otel.semconv-stability.opt-in=service.peer")
  }

  check {
    dependsOn(testStableSemconv)
  }
}
