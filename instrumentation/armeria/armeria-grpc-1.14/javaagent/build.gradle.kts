plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("com.linecorp.armeria")
    module.set("armeria-grpc")
    versions.set("[1.14.0,)")
    assertInverse.set(true)
  }
}

dependencies {
  library("com.linecorp.armeria:armeria-grpc:1.14.0")
  implementation(project(":instrumentation:grpc-1.6:library"))

  testInstrumentation(project(":instrumentation:netty:netty-4.1:javaagent"))
  testInstrumentation(project(":instrumentation:grpc-1.6:javaagent"))

  testImplementation(project(":instrumentation:grpc-1.6:testing"))
  testLibrary("com.linecorp.armeria:armeria-junit5:1.14.0")
}
