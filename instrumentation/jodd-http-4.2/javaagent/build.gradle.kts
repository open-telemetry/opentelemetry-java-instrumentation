plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("org.jodd")
    module.set("jodd-http")
    versions.set("[4.2.0,)")
  }
}

dependencies {
  // 4.2 is the first version with java 8, follow-redirects and HttpRequest#headerOverwrite method
  library("org.jodd:jodd-http:4.2.0")

  testImplementation(project(":instrumentation:jodd-http-4.2:javaagent"))
  testImplementation(project(":instrumentation-api-incubator"))
}

tasks {
  test {
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
