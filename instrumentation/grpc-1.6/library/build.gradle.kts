plugins {
  id("otel.library-instrumentation")
}

val grpcVersion = "1.6.0"

dependencies {
  library("io.grpc:grpc-core:$grpcVersion")

  testLibrary("io.grpc:grpc-netty:$grpcVersion")
  testLibrary("io.grpc:grpc-protobuf:$grpcVersion")
  testLibrary("io.grpc:grpc-services:$grpcVersion")
  testLibrary("io.grpc:grpc-stub:$grpcVersion")

  testImplementation("org.assertj:assertj-core")
  testImplementation(project(":instrumentation:grpc-1.6:testing"))
}
