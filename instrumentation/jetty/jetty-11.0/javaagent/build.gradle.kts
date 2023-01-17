plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("org.eclipse.jetty")
    module.set("jetty-server")
    versions.set("[11,)")
  }
}

dependencies {
  library("org.eclipse.jetty:jetty-server:11.0.0")

  implementation(project(":instrumentation:jetty:jetty-common:javaagent"))
  implementation(project(":instrumentation:servlet:servlet-5.0:javaagent"))
  bootstrap(project(":instrumentation:servlet:servlet-common:bootstrap"))

  testInstrumentation(project(":instrumentation:jetty:jetty-8.0:javaagent"))

  testLibrary("org.eclipse.jetty:jetty-servlet:11.0.0")
}

otelJava {
  minJavaVersionSupported.set(JavaVersion.VERSION_11)
}
