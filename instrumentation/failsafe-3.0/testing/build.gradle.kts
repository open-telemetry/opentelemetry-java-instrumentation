plugins {
  id("otel.java-conventions")
}

dependencies {
  api("io.opentelemetry.javaagent:opentelemetry-testing-common")
  api("org.mockito:mockito-core")
  api("org.mockito:mockito-junit-jupiter")

  compileOnly("dev.failsafe:failsafe:3.0.1")
}
