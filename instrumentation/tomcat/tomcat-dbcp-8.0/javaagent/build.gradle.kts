plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("org.apache.tomcat")
    module.set("tomcat-dbcp")
    versions.set("[8.0.3,)")
    assertInverse.set(true)
  }
}

dependencies {
  library("org.apache.tomcat:tomcat-dbcp:8.0.3")

  implementation(project(":instrumentation:jdbc:javaagent-common"))
  bootstrap(project(":instrumentation:jdbc:bootstrap"))
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
