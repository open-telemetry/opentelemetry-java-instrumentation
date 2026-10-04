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

  implementation(project(":instrumentation:jdbc:javaagent-common"))
  bootstrap(project(":instrumentation:jdbc:bootstrap"))

  testImplementation("org.apache.tomcat:tomcat-jdbc:8.5.0")
}

tasks {
  withType<Test>().configureEach {
    systemProperty("collectMetadata", otelProps.collectMetadata)
  }
}
