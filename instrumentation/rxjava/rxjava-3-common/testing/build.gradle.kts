plugins {
  id("otel.java-conventions")
}

dependencies {
  api(project(":testing-common"))

  api("io.reactivex.rxjava3:rxjava:3.0.12")

  implementation(project(":instrumentation-annotations"))
  implementation("io.opentelemetry:opentelemetry-extension-annotations")
  implementation("com.google.guava:guava")
  implementation("io.opentelemetry:opentelemetry-api")
}
