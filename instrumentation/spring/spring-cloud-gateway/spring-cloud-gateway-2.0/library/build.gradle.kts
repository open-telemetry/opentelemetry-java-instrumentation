plugins {
  id("otel.library-instrumentation")
  id("otel.nullaway-conventions")
}

dependencies {
  library("org.springframework.cloud:spring-cloud-starter-gateway:2.0.0.RELEASE")

  implementation(
    project(":instrumentation:spring:spring-cloud-gateway:spring-cloud-gateway-common:library")
  )

  testImplementation(
    project(":instrumentation:spring:spring-webflux:spring-webflux-5.3:library")
  )
  testLibrary("org.springframework.boot:spring-boot-starter-test:2.7.18")
  testLibrary("org.springframework.boot:spring-boot-starter-webflux:2.7.18")
  testLibrary("org.springframework.cloud:spring-cloud-starter-gateway:3.1.8")

  latestDepTestLibrary(
    "org.springframework.cloud:spring-cloud-starter-gateway-server-webflux:latest.release"
  )
}

if (otelProps.testLatestDeps) {
  configurations.testImplementation {
    exclude(
      group = "org.springframework.cloud",
      module = "spring-cloud-starter-gateway",
    )
  }

  otelJava {
    minJavaVersionSupported.set(JavaVersion.VERSION_17)
  }
} else {
  configurations.testRuntimeClasspath {
    resolutionStrategy {
      force("ch.qos.logback:logback-classic:1.2.13")
      force("org.slf4j:slf4j-api:1.7.36")
    }
  }
}
