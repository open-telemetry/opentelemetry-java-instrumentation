plugins {
  id("otel.java-conventions")
}

dependencies {
  api("io.opentelemetry.instrumentation:opentelemetry-instrumentation-testing")

  compileOnly("com.mchange:c3p0:0.9.2")
}
