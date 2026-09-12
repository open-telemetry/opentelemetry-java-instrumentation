plugins {
  id("otel.javaagent-instrumentation")
}

dependencies {
  compileOnly(project(":muzzle")) // For @NoMuzzle

  compileOnly("org.apache.hbase:hbase-client:1.0.0")

  compileOnly("com.google.auto.value:auto-value-annotations")
  annotationProcessor("com.google.auto.value:auto-value")
}
