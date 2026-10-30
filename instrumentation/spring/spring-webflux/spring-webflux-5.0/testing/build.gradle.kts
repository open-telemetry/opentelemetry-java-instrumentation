plugins {
  id("otel.java-conventions")
}

dependencies {
  implementation("io.opentelemetry.javaagent:opentelemetry-testing-common")

  compileOnly("org.springframework:spring-webflux:5.0.0.RELEASE")
  compileOnly("io.projectreactor.ipc:reactor-netty:0.7.0.RELEASE")
  compileOnly("org.springframework.boot:spring-boot:2.0.0.RELEASE")
  compileOnly("org.springframework.boot:spring-boot-autoconfigure:2.0.0.RELEASE")
}
