plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("org.eclipse.jetty")
    module.set("jetty-server")
    versions.set("[12,)")
  }
}

dependencies {
  library("org.eclipse.jetty:jetty-server:12.0.0")

  bootstrap(project(":instrumentation:servlet:servlet-common:bootstrap"))
  implementation(project(":instrumentation:servlet:servlet-common:javaagent"))

  testInstrumentation(project(":instrumentation:jetty:jetty-8.0:javaagent"))
  testInstrumentation(project(":instrumentation:jetty:jetty-11.0:javaagent"))

  testLibrary("org.eclipse.jetty.ee10:jetty-ee10-servlet:12.0.0")
}

otelJava {
  minJavaVersionSupported.set(JavaVersion.VERSION_17)
}
