plugins {
  id("otel.javaagent-instrumentation")
}

dependencies {
  compileOnly("org.apache.hbase:hbase-client:1.4.0")

  compileOnly("com.google.auto.value:auto-value-annotations")
  annotationProcessor("com.google.auto.value:auto-value")
}
