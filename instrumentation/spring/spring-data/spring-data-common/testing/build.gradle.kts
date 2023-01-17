plugins {
  id("otel.java-conventions")
}

dependencies {
  implementation(project(":testing-common"))

  compileOnly("org.hibernate:hibernate-core:4.3.0.Final")
  compileOnly("org.springframework.data:spring-data-commons:1.8.0.RELEASE")
  compileOnly("org.springframework.data:spring-data-jpa:1.8.0.RELEASE")
  compileOnly("org.springframework:spring-test:3.0.0.RELEASE")
}
