plugins {
  id("otel.java-conventions")
}

dependencies {
  api("io.opentelemetry.javaagent:opentelemetry-testing-common")

  api("org.quartz-scheduler:quartz:2.0.0")
}
