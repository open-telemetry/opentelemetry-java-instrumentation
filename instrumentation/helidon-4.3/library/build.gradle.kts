plugins {
  id("otel.library-instrumentation")
}

otelJava {
  minJavaVersionSupported.set(JavaVersion.VERSION_21)
  if (otelProps.testLatestDeps) {
    maxJavaVersionSupported.set(JavaVersion.VERSION_27)
  }
}

dependencies {
  library("io.helidon.webserver:helidon-webserver:4.3.0")
  testImplementation(project(":instrumentation:helidon-4.3:testing"))
  // 27.0.0 requires java 27, limit the latest dep version for now
  latestDepTestLibrary("io.helidon.webserver:helidon-webserver:27.0.0") // documented limitation
}
