plugins {
  id("otel.library-instrumentation")
}

dependencies {
  compileOnly("io.netty:netty-codec-http:4.1.0.Final")
}
