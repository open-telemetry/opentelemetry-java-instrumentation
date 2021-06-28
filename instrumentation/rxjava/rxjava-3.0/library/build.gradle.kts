plugins {
  id("otel.library-instrumentation")
}

dependencies {
  library("io.reactivex.rxjava3:rxjava:3.0.12")

  testImplementation(project(":instrumentation:rxjava:rxjava-3.0:testing"))
}
