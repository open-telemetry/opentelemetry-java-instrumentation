plugins {
  id("otel.java-conventions")

  war
}

dependencies {
  testImplementation(project(":instrumentation:runtime-telemetry:runtime-telemetry-java8:javaagent"))

  testImplementation("io.opentelemetry:opentelemetry-sdk-testing")

  // Bring in various archives to test introspection logic
  testImplementation("io.opentelemetry:opentelemetry-api")
  testImplementation("io.opentelemetry:opentelemetry-api-incubator")
  testImplementation("org.springframework:spring-webmvc:3.1.0.RELEASE")
  testImplementation("com.google.guava:guava")
}

tasks.war {
  archiveFileName.set("app.war")
  manifest {
    attributes(
      "Implementation-Title" to "Dummy App",
      "Implementation-Vendor" to "OpenTelemetry",
    )
  }
}

tasks.named("test") {
  dependsOn(tasks.getByName("war"))
}

tasks {
  withType<Test>().configureEach {
    environment(
      mapOf(
        // Expose dummy app war location to test
        "DUMMY_APP_WAR" to "${layout.buildDirectory.asFile.get()}/libs/app.war"
      )
    )
  }
}
