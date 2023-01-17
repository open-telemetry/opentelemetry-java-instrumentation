plugins {
  id("otel.javaagent-testing")
}

dependencies {
  testInstrumentation(project(":instrumentation:jaxrs:jaxrs-2.0:jaxrs-2.0-jersey-2.0:javaagent"))
  testInstrumentation(project(":instrumentation:jaxrs:jaxrs-2.0:jaxrs-2.0-annotations:javaagent"))
  testInstrumentation(project(":instrumentation:servlet:servlet-3.0:javaagent"))

  // First version with DropwizardTestSupport:
  testImplementation("io.dropwizard:dropwizard-testing:0.8.0")
  testImplementation("javax.xml.bind:jaxb-api:2.2.3")
  testImplementation("com.fasterxml.jackson.module:jackson-module-afterburner")
}

configurations.testRuntimeClasspath {
  resolutionStrategy {
    // Requires old Guava. Can't use enforcedPlatform since predates BOM
    force("com.google.guava:guava:19.0")

    // requires old logback (and therefore also old slf4j)
    force("ch.qos.logback:logback-classic:1.2.11")
    force("org.slf4j:slf4j-api:1.7.36")
  }
}
