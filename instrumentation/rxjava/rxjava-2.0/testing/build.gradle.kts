plugins {
  id("otel.java-conventions")
}

dependencies {
  api(project(":testing-common"))
  api("io.reactivex.rxjava2:rxjava:2.1.3")

  implementation("io.opentelemetry:opentelemetry-api")
}
