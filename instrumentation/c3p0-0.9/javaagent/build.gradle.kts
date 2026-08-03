plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("com.mchange")
    module.set("c3p0")
    versions.set("(,)")
  }
}

dependencies {
  // first non pre-release version available on maven central
  library("com.mchange:c3p0:0.9.2")

  implementation(project(":instrumentation:c3p0-0.9:library"))
  bootstrap(project(":instrumentation:jdbc:bootstrap"))
  compileOnly(
    project(
      path = ":instrumentation:jdbc:library",
      configuration = "shadow",
    ),
  )

  testImplementation(project(":instrumentation:c3p0-0.9:testing"))
  testInstrumentation(project(":instrumentation:jdbc:javaagent"))
}

tasks {
  withType<Test>().configureEach {
    systemProperty("collectMetadata", otelProps.collectMetadata)
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
