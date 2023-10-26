plugins {
  id("otel.java-conventions")
}

dependencies {
  implementation(project(":testing-common"))

  compileOnly("org.springframework.boot:spring-boot-starter-test:2.0.0.RELEASE")
}
