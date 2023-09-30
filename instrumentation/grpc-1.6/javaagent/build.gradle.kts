plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("io.grpc")
    module.set("grpc-core")
    versions.set("[1.6.0,)")
    assertInverse.set(true)
  }
}

val grpcVersion = "1.6.0"

dependencies {
  implementation(project(":instrumentation:grpc-1.6:library"))

  library("io.grpc:grpc-core:$grpcVersion")

  testInstrumentation(project(":instrumentation:netty:netty-4.1:javaagent"))

  testLibrary("io.grpc:grpc-netty:$grpcVersion")
  testLibrary("io.grpc:grpc-protobuf:$grpcVersion")
  testLibrary("io.grpc:grpc-services:$grpcVersion")
  testLibrary("io.grpc:grpc-stub:$grpcVersion")

  testImplementation(project(":instrumentation:grpc-1.6:testing"))
}

tasks {
  test {
    systemProperty("testLatestDeps", findProperty("testLatestDeps") as Boolean)
    // The agent context debug mechanism isn't compatible with the bridge approach which may add a
    // gRPC context to the root.
    jvmArgs("-Dotel.javaagent.experimental.thread-propagation-debugger.enabled=false")
    jvmArgs("-Dotel.instrumentation.grpc.capture-metadata.client.request=some-client-key")
    jvmArgs("-Dotel.instrumentation.grpc.capture-metadata.server.request=some-server-key")

    // exclude our grpc library instrumentation, the ContextStorageOverride contained within it
    // breaks the tests
    classpath = classpath.filter {
      !it.absolutePath.contains("opentelemetry-grpc-1.6")
    }
  }
}
