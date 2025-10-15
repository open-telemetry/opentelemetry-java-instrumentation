plugins {
  id("otel.library-instrumentation")
}

dependencies {
  library("io.reactivex.rxjava3:rxjava:3.0.12")
  implementation(project(":instrumentation-annotations-support"))
  implementation(project(":instrumentation:rxjava:rxjava-3-common:library"))

  testImplementation(project(":instrumentation:rxjava:rxjava-3-common:testing"))

  latestDepTestLibrary("io.reactivex.rxjava3:rxjava:3.1.0") // see rxjava-3.1.1 module
}
