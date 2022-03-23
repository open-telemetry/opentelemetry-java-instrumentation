plugins {
  id("otel.library-instrumentation")
}

dependencies {
  compileOnly("org.springframework:spring-web:3.1.0.RELEASE")

  testImplementation("org.springframework:spring-web:3.1.0.RELEASE")

  testImplementation(project(":testing-common"))
  testImplementation("io.opentelemetry:opentelemetry-sdk-testing")
}
