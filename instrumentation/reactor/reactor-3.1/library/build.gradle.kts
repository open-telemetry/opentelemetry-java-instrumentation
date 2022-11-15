plugins {
  id("otel.library-instrumentation")
}

dependencies {
  library("io.projectreactor:reactor-core:3.1.0.RELEASE")
  implementation(project(":instrumentation-annotations-support"))
  testLibrary("io.projectreactor:reactor-test:3.1.0.RELEASE")

  testImplementation(project(":instrumentation:reactor:reactor-3.1:testing"))

  latestDepTestLibrary("io.projectreactor:reactor-core:3.4.+")
  latestDepTestLibrary("io.projectreactor:reactor-test:3.4.+")
}
