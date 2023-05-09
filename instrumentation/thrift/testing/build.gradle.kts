plugins {
    id("otel.java-conventions")
    id("otel.protobuf-conventions")
}


dependencies {
  api(project(":testing-common"))
  implementation("io.opentelemetry:opentelemetry-api")
  compileOnly("org.apache.thrift:libthrift:0.14.1")
}










