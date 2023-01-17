plugins {
  id("otel.library-instrumentation")
}

dependencies {
  library("io.netty:netty-codec-http:4.1.0.Final")
  implementation(project(":instrumentation:netty:netty-4-common:library"))
  implementation(project(":instrumentation:netty:netty-common:library"))

  testImplementation(project(":instrumentation:netty:netty-4.1:testing"))
}