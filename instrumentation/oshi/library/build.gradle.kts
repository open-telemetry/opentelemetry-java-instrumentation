plugins {
  id("otel.library-instrumentation")
  id("com.google.osdetector")
}

// 5.5.0 is the first version that works on arm mac
val oshiVersion = if (osdetector.os == "osx" && osdetector.arch == "aarch_64") "5.5.0" else "5.3.1"

dependencies {
  library("com.github.oshi:oshi-core:$oshiVersion")

  testImplementation(project(":instrumentation:oshi:testing"))
}
