plugins {
  id("otel.java-conventions")
}

dependencies {
  compileOnly("com.vaadin:vaadin-spring-boot-starter:14.2.0")

  api("org.testcontainers:selenium")
  implementation(project(":testing-common"))
  implementation("org.seleniumhq.selenium:selenium-java:4.8.3")
}
tasks.withType<Test>().configureEach {
  jvmArgs("-Dotel.instrumentation.common.experimental.controller-telemetry.enabled=true")
  jvmArgs("-Dotel.instrumentation.common.experimental.view-telemetry.enabled=true")
}
