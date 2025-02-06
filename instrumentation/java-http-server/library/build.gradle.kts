plugins {
  id("otel.library-instrumentation")
  id("otel.nullaway-conventions")
}

dependencies {
  testImplementation(project(":instrumentation:java-http-server:testing"))
}

otelJava {
  minJavaVersionSupported.set(JavaVersion.VERSION_17)
}

tasks {
  withType<Test>().configureEach {
    systemProperty("testLatestDeps", findProperty("testLatestDeps") as Boolean)
  }
}
