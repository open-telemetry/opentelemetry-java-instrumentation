plugins {
  id("otel.library-instrumentation")
}

dependencies {
  library("org.apache.iceberg:iceberg-core:1.8.1")
}
