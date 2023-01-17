plugins {
  id("otel.javaagent-instrumentation")
}

// This module is only used as a dependency for other javaagent modules and does not contain any
// non-abstract implementations of InstrumentationModule

dependencies {
  implementation(project(":instrumentation:servlet:servlet-common:javaagent"))

  compileOnly("javax.servlet:servlet-api:2.3")

  // We don't check testLatestDeps for this module since we have coverage in others like servlet-3.0
  testImplementation("org.eclipse.jetty:jetty-server:7.0.0.v20091005")
  testImplementation("org.eclipse.jetty:jetty-servlet:7.0.0.v20091005")
}
