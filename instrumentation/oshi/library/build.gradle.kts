plugins {
  id("otel.library-instrumentation")
  id("com.google.osdetector")
}

dependencies {
  // 5.5.0 is the first version that works on arm mac
  library("com.github.oshi:oshi-core:5.5.0")

  testImplementation(project(":instrumentation:oshi:testing"))
}
