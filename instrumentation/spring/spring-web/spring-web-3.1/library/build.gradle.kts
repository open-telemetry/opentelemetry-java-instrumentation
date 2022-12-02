plugins {
  id("otel.library-instrumentation")
}

dependencies {
  compileOnly("org.springframework:spring-web:3.1.0.RELEASE")

  testLibrary("org.springframework:spring-web:3.1.0.RELEASE")
  latestDepTestLibrary("org.springframework:spring-web:5.+")

  testImplementation(project(":testing-common"))
  testImplementation("io.opentelemetry:opentelemetry-sdk-testing")
}
