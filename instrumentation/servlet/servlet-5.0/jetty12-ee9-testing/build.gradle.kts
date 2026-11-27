plugins {
  id("otel.javaagent-testing")
}

dependencies {
  testInstrumentation(project(":instrumentation:servlet:servlet-5.0:javaagent"))
  testInstrumentation(project(":instrumentation:jetty:jetty-8.0:javaagent"))
  testInstrumentation(project(":instrumentation:jetty:jetty-11.0:javaagent"))
  testInstrumentation(project(":instrumentation:jetty:jetty-12.0:javaagent"))
  testImplementation(project(":instrumentation:servlet:servlet-5.0:testing"))

  library("org.eclipse.jetty.ee9:jetty-ee9-servlet:12.0.6")
  latestDepTestLibrary("org.eclipse.jetty.ee9:jetty-ee9-servlet:12.+") // see test suite below
}

otelJava {
  minJavaVersionSupported.set(JavaVersion.VERSION_17)
}
