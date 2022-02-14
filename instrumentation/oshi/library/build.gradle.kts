plugins {
  id("otel.library-instrumentation")
}

dependencies {
  compileOnly("com.github.oshi:oshi-core:5.3.1")
  testImplementation("com.github.oshi:oshi-core:5.3.1")

  testImplementation(project(":instrumentation:oshi:testing"))

  // Could not parse POM https://repo.maven.apache.org/maven2/com/github/oshi/oshi-core/6.1.1/oshi-core-6.1.1.pom
  latestDepTestLibrary("com.github.oshi:oshi-core:6.1.0")
}
