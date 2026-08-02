plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("org.apache.commons")
    module.set("commons-dbcp2")
    versions.set("[2,)")
    assertInverse.set(true)
  }
}

dependencies {
  library("org.apache.commons:commons-dbcp2:2.0")

  implementation(project(":instrumentation:apache-dbcp-2.0:library"))

  bootstrap(project(":instrumentation:jdbc:bootstrap"))
  compileOnly(
    project(
      path = ":instrumentation:jdbc:library",
      configuration = "shadow",
    ),
  )

  testImplementation(project(":instrumentation:apache-dbcp-2.0:testing"))
  testInstrumentation(project(":instrumentation:jdbc:javaagent"))
}

tasks {
  withType<Test>().configureEach {
    systemProperty("collectMetadata", otelProps.collectMetadata)
    // The JDBC javaagent supplies the URL parser; its connection advice is not under test here.
    jvmArgs("-Dotel.instrumentation.jdbc.enabled=false")
  }

  val testStableSemconv = register<Test>("testStableSemconv") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath

    jvmArgs("-Dotel.semconv-stability.opt-in=database")
    systemProperty("metadataConfig", "otel.semconv-stability.opt-in=database")
  }

  check {
    dependsOn(testStableSemconv)
  }
}
