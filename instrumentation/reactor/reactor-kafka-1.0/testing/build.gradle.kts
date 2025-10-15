plugins {
  id("otel.java-conventions")
}

dependencies {
  api(project(":testing-common"))

  compileOnly("io.projectreactor.kafka:reactor-kafka:1.0.0.RELEASE")
  implementation("org.testcontainers:testcontainers-kafka")
}
