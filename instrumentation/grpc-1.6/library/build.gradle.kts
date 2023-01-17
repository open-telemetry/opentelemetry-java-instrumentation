plugins {
  id("otel.library-instrumentation")
  id("otel.animalsniffer-conventions")
}

val grpcVersion = "1.6.0"

dependencies {
  library("io.grpc:grpc-core:$grpcVersion")

  testLibrary("io.grpc:grpc-netty:$grpcVersion")
  testLibrary("io.grpc:grpc-protobuf:$grpcVersion")
  testLibrary("io.grpc:grpc-services:$grpcVersion")
  testLibrary("io.grpc:grpc-stub:$grpcVersion")

  testImplementation(project(":instrumentation:grpc-1.6:testing"))
}
