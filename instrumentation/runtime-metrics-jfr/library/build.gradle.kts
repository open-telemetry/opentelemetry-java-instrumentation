plugins {
  id("otel.library-instrumentation")
}

otelJava {
  minJavaVersionSupported.set(JavaVersion.VERSION_17)
}

dependencies {
//  implementation(project(":instrumentation-api"))
  implementation("io.opentelemetry:opentelemetry-api")

//  testImplementation("io.opentelemetry:opentelemetry-sdk-metrics")
//  testImplementation(project(":testing-common"))
}
