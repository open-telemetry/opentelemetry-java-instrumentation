plugins {
  id("otel.library-instrumentation")
}

dependencies {
  library("io.micrometer:micrometer-core:1.5.0")

  testImplementation(project(":instrumentation:micrometer:micrometer-1.5:testing"))
}

tasks {
  val testV3Preview = register<Test>("testV3Preview") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    jvmArgs("-Dotel.instrumentation.common.v3-preview=true")
  }

  check {
    dependsOn(testV3Preview)
  }
}
