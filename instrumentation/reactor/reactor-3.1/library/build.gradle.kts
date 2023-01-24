plugins {
  id("otel.library-instrumentation")
}

dependencies {
  library("io.projectreactor:reactor-core:3.5.0")
  implementation(project(":instrumentation-annotations-support"))
  testLibrary("io.projectreactor:reactor-test:3.5.0")

  testImplementation(project(":instrumentation:reactor:reactor-3.1:testing"))
}
