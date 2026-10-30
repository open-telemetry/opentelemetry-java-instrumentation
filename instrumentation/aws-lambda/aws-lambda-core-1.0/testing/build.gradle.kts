plugins {
  id("otel.java-conventions")
}

dependencies {
  api("io.opentelemetry.javaagent:opentelemetry-instrumentation-testing")

  api("com.amazonaws:aws-lambda-java-core:1.0.0")

  api("org.junit-pioneer:junit-pioneer")
  api("org.mockito:mockito-junit-jupiter")

  implementation("io.opentelemetry:opentelemetry-api")
}
