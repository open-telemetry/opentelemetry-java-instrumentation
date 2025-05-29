plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("org.eclipse.jetty")
    module.set("jetty-client")
    versions.set("[12,)")
  }
}

otelJava {
  minJavaVersionSupported.set(JavaVersion.VERSION_17)
}

dependencies {
  implementation(project(":instrumentation:jetty-httpclient:jetty-httpclient-12.0:library"))

  library("org.eclipse.jetty:jetty-client:12.0.0")

  testInstrumentation(project(":instrumentation:jetty-httpclient:jetty-httpclient-9.2:javaagent"))

  testImplementation(project(":instrumentation:jetty-httpclient:jetty-httpclient-12.0:testing"))
}
