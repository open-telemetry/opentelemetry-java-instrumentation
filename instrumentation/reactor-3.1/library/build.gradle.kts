plugins {
  id("otel.library-instrumentation")
}

dependencies {
  library("io.projectreactor:reactor-core:3.1.0.RELEASE")
  implementation(project(":instrumentation-api-annotation-support"))
  testLibrary("io.projectreactor:reactor-test:3.1.0.RELEASE")

  testImplementation(project(":instrumentation:reactor-3.1:testing"))

  // Looks like later versions on reactor need this dependency for some reason even though it is marked as optional.
  latestDepTestLibrary("io.micrometer:micrometer-core:1.+")
}
