plugins {
  id("otel.javaagent-instrumentation")
}

otelJava {
  // DO NOT MERGE: For some reason below hack doesn't work on this module and produces no error message!!!
  minJavaVersionSupported.set(JavaVersion.VERSION_1_9)
}
