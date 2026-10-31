import io.opentelemetry.instrumentation.gradle.executions

plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("io.opentelemetry")
    module.set("opentelemetry-extension-annotations")
    versions.set("[0.16.0,)")
    assertInverse.set(true)
  }
}

dependencies {
  compileOnly(project(":instrumentation-annotations-support"))

  // this instrumentation needs to do similar shading dance as opentelemetry-api-1.0 because
  // the @WithSpan annotation references the OpenTelemetry API's SpanKind class
  //
  // see the comment in opentelemetry-api-1.0.gradle for more details
  compileOnly(project(":opentelemetry-ext-annotations-shaded-for-instrumenting", configuration = "shadow"))

  testCompileOnly("com.google.code.findbugs:annotations")

  testImplementation("io.opentelemetry:opentelemetry-extension-annotations")
  testImplementation(project(":instrumentation-annotations-support"))
  testImplementation("net.bytebuddy:byte-buddy")
}

tasks {
  compileTestJava {
    options.compilerArgs.add("-parameters")
  }
  test {
    jvmArgs("-Dotel.instrumentation.opentelemetry-annotations.exclude-methods=io.opentelemetry.test.annotation.TracedWithSpan[ignored]")
  }
}

testing.suites.named<JvmTestSuite>("test") {
  executions {
    register("DeclarativeConfig") {
      filter {
        includeTestsMatching("WithSpanInstrumentationTest.excludedMethod")
        includeTestsMatching("WithSpanInstrumentationTest.deriveAutomaticName")
      }
      jvmArgs(
        "-Dotel.config.file=$projectDir/src/test/resources/declarative-config.yaml"
      )
    }
  }
}
