plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    coreJdk()
  }
}

otelJava {
  minJavaVersionSupported.set(JavaVersion.VERSION_11)
}
