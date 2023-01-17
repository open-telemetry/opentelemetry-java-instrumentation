plugins {
  id("otel.java-conventions")
}

dependencies {
  api(project(":testing-common"))

  api("io.projectreactor:reactor-core:3.1.0.RELEASE")

  implementation("io.opentelemetry:opentelemetry-api")
}
