plugins {
  id("otel.java-conventions")
}

dependencies {
  implementation("io.opentelemetry.javaagent:opentelemetry-testing-common")

  compileOnly("org.springframework:spring-webflux:5.0.0.RELEASE")
  compileOnly("org.springframework.boot:spring-boot-starter-reactor-netty:2.0.0.RELEASE")
  compileOnly("org.springframework.boot:spring-boot:2.0.0.RELEASE")
  compileOnly("org.springframework.boot:spring-boot-starter-test:2.0.0.RELEASE")
}
