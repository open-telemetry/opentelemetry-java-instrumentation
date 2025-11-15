plugins {
  id("otel.library-instrumentation")
  id("otel.nullaway-conventions")
}

dependencies {
  library("org.apache.iceberg:iceberg-core:1.8.1")
  testImplementation(project(":instrumentation:iceberg-1.8:testing"))
}

otelJava {
  minJavaVersionSupported.set(JavaVersion.VERSION_11)
}
