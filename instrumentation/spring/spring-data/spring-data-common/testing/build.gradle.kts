plugins {
  id("otel.java-conventions")
}

dependencies {
  implementation("io.opentelemetry.javaagent:opentelemetry-javaagent-instrumentation-testing")

  compileOnly(platform("org.springframework:spring-framework-bom:3.2.10.RELEASE"))
  compileOnly("org.hibernate:hibernate-core:4.3.0.Final")
  compileOnly("org.springframework.data:spring-data-commons:1.8.0.RELEASE")
  compileOnly("org.springframework.data:spring-data-jpa:1.6.0.RELEASE")
  compileOnly("org.springframework:spring-test:3.2.10.RELEASE")
}
