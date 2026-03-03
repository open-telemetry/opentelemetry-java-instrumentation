plugins {
  id("otel.library-instrumentation")
}

dependencies {
  library("io.reactivex.rxjava3:rxjava:3.1.1")
  implementation(project(":instrumentation-annotations-support"))
  implementation(project(":instrumentation:rxjava:rxjava-common-3.0:library"))

  testImplementation(project(":instrumentation:rxjava:rxjava-common-3.0:testing"))
}
