plugins {
  id("otel.java-conventions")
}

dependencies {
  implementation(project(":testing-common"))
  implementation("org.testcontainers:kafka")

  compileOnly("org.springframework.kafka:spring-kafka:2.7.0")
  compileOnly("org.springframework.boot:spring-boot-starter-test:2.5.3")
  compileOnly("org.springframework.boot:spring-boot-starter:2.5.3")
}
