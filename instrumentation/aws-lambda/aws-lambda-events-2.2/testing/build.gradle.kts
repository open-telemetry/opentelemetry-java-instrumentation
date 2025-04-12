plugins {
  id("otel.java-conventions")
}

dependencies {
  api(project(":testing-common"))

  api("com.amazonaws:aws-lambda-java-core:1.0.0")
  api("com.amazonaws:aws-lambda-java-events:2.2.1")

  api("org.junit-pioneer:junit-pioneer")
  api("org.mockito:mockito-junit-jupiter")

  implementation("io.opentelemetry:opentelemetry-api")
  implementation("com.github.stefanbirkner:system-lambda")
}
