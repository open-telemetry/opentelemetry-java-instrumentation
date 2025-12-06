plugins {
  id("otel.library-instrumentation")
  id("otel.nullaway-conventions")
}

val springBootVersion = "2.6.15"

dependencies {
  compileOnly("org.springframework:spring-webmvc:5.3.0")
  compileOnly("javax.servlet:javax.servlet-api:4.0.1")

  testLibrary("org.springframework.boot:spring-boot-starter-web:$springBootVersion")
  testLibrary("org.springframework.boot:spring-boot-starter-test:$springBootVersion") {
    exclude("org.junit.vintage", "junit-vintage-engine")
  }

  latestDepTestLibrary("org.springframework.boot:spring-boot-starter-web:2.+") // see spring-webmvc-6.0 module
  latestDepTestLibrary("org.springframework.boot:spring-boot-starter-test:2.+") // see spring-webmvc-6.0 module
}

configurations.testRuntimeClasspath {
  resolutionStrategy {
    // requires old logback (and therefore also old slf4j)
    force("ch.qos.logback:logback-classic:1.2.11")
    force("org.slf4j:slf4j-api:1.7.36")
  }
}

tasks {
  test {
    systemProperty("collectMetadata", findProperty("collectMetadata")?.toString() ?: "false")
  }
}
