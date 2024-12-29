plugins {
  id("otel.javaagent-testing")
}

dependencies {
  testImplementation("javax.ws.rs:javax.ws.rs-api:2.0.1")

  testLibrary("org.glassfish.jersey.core:jersey-client:2.0")
  testLibrary("org.jboss.resteasy:resteasy-client:3.0.5.Final")
  // ^ This version has timeouts https://issues.redhat.com/browse/RESTEASY-975
  testLibrary("org.apache.cxf:cxf-rt-rs-client:3.1.0")
  // Doesn't work with CXF 3.0.x because their context is wrong:
  // https://github.com/apache/cxf/commit/335c7bad2436f08d6d54180212df5a52157c9f21

  testImplementation("javax.xml.bind:jaxb-api:2.2.3")

  testInstrumentation(project(":instrumentation:apache-httpclient:apache-httpclient-4.0:javaagent"))
  testInstrumentation(project(":instrumentation:http-url-connection:javaagent"))
  testInstrumentation(project(":instrumentation:java-http-client:javaagent"))

  // there's no test coverage beyond this, but there's no reason to believe it wouldn't work
  // also note that this isn't really documented on supported-libraries.md because there's not
  // really any instrumentation for it, as it just relies on other http client instrumentations
  latestDepTestLibrary("org.glassfish.jersey.inject:jersey-hk2:2.+")
  latestDepTestLibrary("org.glassfish.jersey.core:jersey-client:2.+")
  latestDepTestLibrary("org.jboss.resteasy:resteasy-client:3.0.26.Final")
  latestDepTestLibrary("org.apache.cxf:cxf-rt-rs-client:3.+")
}

// Requires old Guava. Can't use enforcedPlatform since predates BOM
configurations.testRuntimeClasspath.get().resolutionStrategy.force("com.google.guava:guava:19.0")

tasks.withType<Test>().configureEach {
  // required on jdk17
  jvmArgs("--add-opens=java.base/java.net=ALL-UNNAMED")
  jvmArgs("-XX:+IgnoreUnrecognizedVMOptions")

  systemProperty("testLatestDeps", findProperty("testLatestDeps") as Boolean)
}
