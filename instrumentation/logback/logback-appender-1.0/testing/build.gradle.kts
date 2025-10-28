plugins {
  id("otel.java-conventions")
}

dependencies {
  api(project(":testing-common"))
  api("io.opentelemetry:opentelemetry-sdk-testing")

  api("ch.qos.logback:logback-classic:1.0.0")
  api("org.slf4j:slf4j-api:1.7.36")
}
