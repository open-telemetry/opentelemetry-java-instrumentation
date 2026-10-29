plugins {
  id("otel.java-conventions")
}

dependencies {
  api("io.opentelemetry.javaagent:opentelemetry-javaagent-instrumentation-testing")

  api("io.netty:netty-codec-http:4.1.0.Final")
}
