plugins {
  id("otel.library-instrumentation")
}

dependencies {
  library("org.springframework:spring-webflux:5.0.0.RELEASE")
  
  compileOnly("io.projectreactor.ipc:reactor-netty:0.7.0.RELEASE")

  testImplementation(project(":instrumentation:spring:spring-webflux:spring-webflux-5.0:testing"))

  testLibrary("org.springframework.boot:spring-boot-starter-webflux:2.0.0.RELEASE")
  testLibrary("org.springframework.boot:spring-boot-starter-test:2.0.0.RELEASE")
  testLibrary("org.springframework.boot:spring-boot-starter-reactor-netty:2.0.0.RELEASE")
}

val latestDepTest = findProperty("testLatestDeps") as Boolean

// spring 6 (which spring-kafka 3.+ uses) requires java 17
if (latestDepTest) {
  otelJava {
    minJavaVersionSupported.set(JavaVersion.VERSION_17)
  }
}
