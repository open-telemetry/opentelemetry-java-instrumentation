plugins {
  id("otel.java-conventions")
}

dependencies {
  api("io.opentelemetry.javaagent:opentelemetry-javaagent-instrumentation-testing")

  compileOnly("io.javalin:javalin:5.0.0")
}
