plugins {
  id("otel.java-conventions")
}

dependencies {
  api(project(":testing-common"))

  api("com.amazonaws:aws-lambda-java-core:1.0.0")

  api("org.junit-pioneer:junit-pioneer")
  api("org.mockito:mockito-junit-jupiter")

  implementation("com.google.guava:guava")

  implementation("io.opentelemetry:opentelemetry-api")
  implementation("com.github.stefanbirkner:system-lambda")
}
