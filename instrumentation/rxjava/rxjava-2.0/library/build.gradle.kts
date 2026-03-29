plugins {
  id("otel.library-instrumentation")
  id("otel.animalsniffer-conventions")
}

dependencies {
  library("io.reactivex.rxjava2:rxjava:2.1.3")
  implementation(project(":instrumentation-annotations-support"))

  testImplementation(project(":instrumentation:rxjava:rxjava-2.0:testing"))
}
