plugins {
  id("otel.javaagent-instrumentation")
}

dependencies {
  compileOnly(project(":javaagent-bootstrap"))
  compileOnly(project(":javaagent-tooling"))

  testImplementation(project(":javaagent-bootstrap"))

  // This is the earliest version that has org.apache.catalina.loader.ParallelWebappClassLoader
  // which is used in the test
  testLibrary("org.apache.tomcat:tomcat-catalina:8.0.14")

  testImplementation("org.jboss.modules:jboss-modules:1.3.10.Final")

  // TODO: we should separate core and Eclipse tests at some point,
  // but right now core-specific tests are quite dumb and are run with
  // core version provided by Eclipse implementation.
  // testImplementation("org.osgi:org.osgi.core:4.0.0")
  testImplementation("org.eclipse.platform:org.eclipse.osgi:3.13.200")
  testImplementation("org.apache.felix:org.apache.felix.framework:6.0.2")
}
