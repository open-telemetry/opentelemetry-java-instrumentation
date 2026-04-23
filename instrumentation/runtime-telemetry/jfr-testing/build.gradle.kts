plugins {
  id("otel.javaagent-testing")
}

dependencies {
  testInstrumentation(project(":instrumentation:runtime-telemetry:javaagent"))
}

otelJava {
  minJavaVersionSupported.set(JavaVersion.VERSION_17)
}

tasks {
  test {
    // Verify backward compatibility of the deprecated runtime-telemetry-java17.enabled flag
    jvmArgs("-Dotel.instrumentation.runtime-telemetry-java17.enabled=true")
  }

  val testEmitExperimentalJfrMetrics by registering(Test::class) {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath

    jvmArgs("-Dotel.instrumentation.runtime-telemetry.emit-experimental-jfr-metrics=true")
  }

  check {
    dependsOn(testEmitExperimentalJfrMetrics)
  }
}
