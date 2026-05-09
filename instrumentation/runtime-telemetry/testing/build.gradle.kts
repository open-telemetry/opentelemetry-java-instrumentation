plugins {
  id("otel.java-conventions")

  war
}

dependencies {
  testImplementation(project(":instrumentation:runtime-telemetry:javaagent"))

  testImplementation("io.opentelemetry:opentelemetry-sdk-testing")

  // Bring in various archives to test introspection logic
  testImplementation("io.opentelemetry:opentelemetry-api")
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

tasks.test {
  dependsOn(tasks.war)
  environment(
    mapOf(
      // Expose dummy app war location to test
      "DUMMY_APP_WAR" to "${layout.buildDirectory.asFile.get()}/libs/app.war"
    )
  )
}
