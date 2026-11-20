plugins {
  id("otel.java-conventions")
}

dependencies {
  compileOnly("com.vaadin:vaadin-spring-boot-starter:14.2.0")

  implementation("io.opentelemetry.javaagent:opentelemetry-testing-common")
}
