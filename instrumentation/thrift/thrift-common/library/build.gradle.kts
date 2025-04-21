plugins {
  id("otel.library-instrumentation")
}

dependencies {
  library("org.apache.thrift:libthrift:0.7.0")
  compileOnly("javax.annotation:javax.annotation-api:1.3.2")
  compileOnly("com.google.auto.value:auto-value-annotations")
  annotationProcessor("com.google.auto.value:auto-value")
}
