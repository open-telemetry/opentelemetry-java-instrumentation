plugins {
  id("otel.java-conventions")
}

dependencies {
  implementation(project(":testing-common"))

  implementation("org.spockframework:spock-spring")

  compileOnly("org.springframework.boot:spring-boot-starter-test:1.5.17.RELEASE")
  compileOnly("org.springframework.boot:spring-boot-starter-web:1.5.17.RELEASE")
  compileOnly("org.springframework.boot:spring-boot-starter-security:1.5.17.RELEASE")
}