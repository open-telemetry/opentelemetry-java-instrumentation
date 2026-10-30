plugins {
  id("otel.java-conventions")
}

dependencies {
  api("org.junit.jupiter:junit-jupiter-api")

  api("io.opentelemetry.javaagent:opentelemetry-testing-common")
}
