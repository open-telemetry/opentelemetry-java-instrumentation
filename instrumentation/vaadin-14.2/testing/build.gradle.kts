plugins {
  id("otel.java-conventions")
}

dependencies {
  compileOnly("com.vaadin:vaadin-spring-boot-starter:14.2.0")

  api("org.testcontainers:testcontainers-selenium")
  implementation(project(":testing-common"))
  implementation("org.seleniumhq.selenium:selenium-java:4.8.3")
}
