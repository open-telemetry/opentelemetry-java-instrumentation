plugins {
  id("otel.library-instrumentation")
  id("otel.nullaway-conventions")
}

dependencies {
  compileOnly("org.springframework:spring-web:3.1.0.RELEASE")

  testLibrary("org.springframework:spring-web:3.1.0.RELEASE")

  testImplementation("io.opentelemetry:opentelemetry-sdk-testing")
}

// spring 6 requires java 17
if (otelProps.testLatestDeps) {
  otelJava {
    minJavaVersionSupported.set(JavaVersion.VERSION_17)
  }
}
