plugins {
  id("otel.java-conventions")
}

dependencies {
  api(project(":testing-common"))

  api("org.testcontainers:testcontainers")

  compileOnly("org.springframework.integration:spring-integration-core:4.1.0.RELEASE")
  compileOnly("org.springframework.boot:spring-boot-starter-test:1.5.22.RELEASE")
  compileOnly("org.springframework.boot:spring-boot-starter:1.5.22.RELEASE")
  compileOnly("org.springframework.cloud:spring-cloud-stream:2.2.1.RELEASE")
  compileOnly("org.springframework.cloud:spring-cloud-stream-binder-rabbit:2.2.1.RELEASE")
}