plugins {
  id("otel.library-instrumentation")
}

val versions: Map<String, String> by project
val springBootVersion = versions["org.springframework.boot"]

dependencies {
  compileOnly("org.springframework:spring-webmvc:5.3.0")
  compileOnly("javax.servlet:javax.servlet-api:4.0.1")

  testImplementation(project(":testing-common"))
  testImplementation("org.springframework.boot:spring-boot-starter-web:$springBootVersion")
  testImplementation("org.springframework.boot:spring-boot-starter-test:$springBootVersion") {
    exclude("org.junit.vintage", "junit-vintage-engine")
  }
}
