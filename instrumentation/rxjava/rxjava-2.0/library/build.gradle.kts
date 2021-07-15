plugins {
  id("otel.library-instrumentation")
}

dependencies {
  library("io.reactivex.rxjava2:rxjava:2.1.3")
  implementation(project(":instrumentation-api-annotation-support"))

  testImplementation(project(":instrumentation:rxjava:rxjava-2.0:testing"))
}
