plugins {
  id("otel.javaagent-instrumentation")
}

dependencies {
  compileOnly("io.netty:netty-codec-http:4.0.0.Final")
}
