plugins {
  id("otel.library-instrumentation")
}

// module name
val moduleName: String by extra("io.opentelemetry.instrumentation.spring.webmvc.v5_3")

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

configurations.testRuntimeClasspath {
  resolutionStrategy {
    // requires old logback (and therefore also old slf4j)
    force("ch.qos.logback:logback-classic:1.2.11")
    force("org.slf4j:slf4j-api:1.7.36")
  }
}
