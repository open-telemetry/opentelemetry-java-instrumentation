plugins {
  id("otel.javaagent-testing")
}

val versions: Map<String, String> by project

dependencies {
  compileOnly("com.vaadin:vaadin-spring-boot-starter:14.2.0")

  api("org.testcontainers:selenium:${versions["org.testcontainers"]}")
  implementation(project(":testing-common"))
  implementation("org.seleniumhq.selenium:selenium-java:3.141.59")
}
