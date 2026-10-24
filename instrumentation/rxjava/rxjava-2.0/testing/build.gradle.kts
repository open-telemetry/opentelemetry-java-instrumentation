plugins {
  id("otel.java-conventions")
}

dependencies {
  api("io.opentelemetry.javaagent:opentelemetry-testing-common")
  api("io.reactivex.rxjava2:rxjava:2.0.6")

  implementation("io.opentelemetry:opentelemetry-api")
}
