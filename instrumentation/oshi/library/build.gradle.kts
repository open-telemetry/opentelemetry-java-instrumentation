plugins {
  id("otel.library-instrumentation")
  id("com.google.osdetector")
}

dependencies {
  library("com.github.oshi:oshi-core:5.3.1")

  testImplementation(project(":instrumentation:oshi:testing"))
}

if (osdetector.os == "osx" && osdetector.arch == "aarch_64" && !(findProperty("testLatestDeps") as Boolean)) {
  // 5.5.0 is the first version that works on arm mac
  configurations.testRuntimeClasspath.get().resolutionStrategy.force("com.github.oshi:oshi-core:5.5.0")
}
