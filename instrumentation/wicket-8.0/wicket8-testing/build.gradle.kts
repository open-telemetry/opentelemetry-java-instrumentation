plugins {
  id("otel.javaagent-testing")
}

dependencies {
  library("org.apache.wicket:wicket:8.0.0")

  testImplementation(project(":instrumentation:wicket-8.0:common-testing"))
  testImplementation("org.jsoup:jsoup:1.13.1")
  testImplementation("org.eclipse.jetty:jetty-server:8.0.0.v20110901")
  testImplementation("org.eclipse.jetty:jetty-servlet:8.0.0.v20110901")

  testInstrumentation(project(":instrumentation:wicket-8.0:javaagent"))
  testInstrumentation(project(":instrumentation:servlet:servlet-3.0:javaagent"))
  testInstrumentation(project(":instrumentation:servlet:servlet-javax-common:javaagent"))

  latestDepTestLibrary("org.apache.wicket:wicket:9.+") // see wicket10-testing module
}

val latestDepTest = findProperty("testLatestDeps") as Boolean

// Wicket 9 requires Java 11
if (latestDepTest) {
  otelJava {
    minJavaVersionSupported.set(JavaVersion.VERSION_11)
  }
}
