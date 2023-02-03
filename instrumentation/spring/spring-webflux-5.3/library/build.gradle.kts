plugins {
  id("otel.library-instrumentation")
}

dependencies {
  library("org.springframework:spring-webflux:5.3.0")

  implementation(project(":instrumentation:reactor:reactor-3.1:library"))

//  testImplementation(project(":instrumentation:spring:spring-webflux-5.0:testing"))
}

val latestDepTest = findProperty("testLatestDeps") as Boolean

// spring 6 (which spring-kafka 3.+ uses) requires java 17
if (latestDepTest) {
  otelJava {
    minJavaVersionSupported.set(JavaVersion.VERSION_17)
  }
}
