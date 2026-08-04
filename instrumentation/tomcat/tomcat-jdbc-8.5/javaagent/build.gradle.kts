plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("org.apache.tomcat")
    module.set("tomcat-jdbc")
    versions.set("[8.5.0,)")
    // no assertInverse because tomcat-jdbc < 8.5 doesn't have methods that we hook into
  }
}

dependencies {
  compileOnly("org.apache.tomcat:tomcat-jdbc:8.5.0")

  // JdbcConnectionUrlParser is provided by the shaded JDBC library, and its DbInfo return type is
  // relocated to the JDBC bootstrap package.
  bootstrap(project(":instrumentation:jdbc:bootstrap"))
  compileOnly(
    project(
      path = ":instrumentation:jdbc:library",
      configuration = "shadow",
    ),
  )

  testImplementation("org.apache.tomcat:tomcat-jdbc:8.5.0")
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
