plugins {
  id("otel.library-instrumentation")
}

dependencies {
  api(project(":instrumentation:reactor:reactor-common:library"))
  library("io.projectreactor:reactor-core:3.4.0")
  implementation(project(":instrumentation-annotations-support"))
  testLibrary("io.projectreactor:reactor-test:3.4.0")

  testImplementation(project(":instrumentation:reactor:reactor-3.1:testing"))

  latestDepTestLibrary("io.projectreactor:reactor-core:3.4.+")
  latestDepTestLibrary("io.projectreactor:reactor-test:3.4.+")
}
