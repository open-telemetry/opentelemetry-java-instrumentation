plugins {
  id("otel.java-conventions")
}

dependencies {
  implementation(project(":testing-common"))

  compileOnly("org.springframework:spring-webflux:5.0.0.RELEASE")

  // Compile with both old and new netty packages since our test references both for old and
  // latest dep tests.
  compileOnly("io.projectreactor.ipc:reactor-netty:0.7.0.RELEASE")
  compileOnly("io.projectreactor.netty:reactor-netty-http:1.0.7")

  compileOnly("org.springframework.boot:spring-boot:2.1.0.RELEASE")
}
