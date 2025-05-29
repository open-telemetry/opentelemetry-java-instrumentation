plugins {
  id("otel.library-instrumentation")
}

dependencies {
  compileOnly("org.springframework:spring-web:3.1.0.RELEASE")

  testLibrary("org.springframework:spring-web:3.1.0.RELEASE")

  testImplementation(project(":testing-common"))
  testImplementation("io.opentelemetry:opentelemetry-sdk-testing")
}

val latestDepTest = findProperty("testLatestDeps") as Boolean

// spring 6 requires java 17
if (latestDepTest) {
  otelJava {
    minJavaVersionSupported.set(JavaVersion.VERSION_17)
  }
}
