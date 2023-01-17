plugins {
  id("otel.java-conventions")
  id("otel.protobuf-conventions")
}

val grpcVersion = "1.6.0"

dependencies {
  api(project(":testing-common"))

  api("io.grpc:grpc-core:$grpcVersion")
  api("io.grpc:grpc-protobuf:$grpcVersion")
  api("io.grpc:grpc-services:$grpcVersion")
  api("io.grpc:grpc-stub:$grpcVersion")

  implementation("javax.annotation:javax.annotation-api:1.3.2")

  implementation("com.google.guava:guava")

  api("org.junit-pioneer:junit-pioneer")
  implementation("io.opentelemetry:opentelemetry-api")
}

tasks {
  compileJava {
    with(options) {
      // We generate stubs using an old version of protobuf to test old versions of gRPC,
      // where this lint error triggers.
      compilerArgs.add("-Xlint:-cast")
    }
  }
}
