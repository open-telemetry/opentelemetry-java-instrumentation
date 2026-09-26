plugins {
  id("otel.library-instrumentation")
  id("com.google.osdetector")
}

dependencies {
  library("com.github.oshi:oshi-core:5.3.1")

  testImplementation(project(":instrumentation:oshi-5.0:testing"))
}

if (osdetector.os == "osx" && osdetector.arch == "aarch_64" && !otelProps.testLatestDeps) {
  // 5.5.0 is the first version that works on arm mac
  configurations.testRuntimeClasspath.get().resolutionStrategy.force("com.github.oshi:oshi-core:5.5.0")
}

tasks {
  val testV3Preview by registering(Test::class) {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    jvmArgs("-Dotel.instrumentation.common.v3-preview=true")
  }

  check {
    dependsOn(testV3Preview)
  }
}
