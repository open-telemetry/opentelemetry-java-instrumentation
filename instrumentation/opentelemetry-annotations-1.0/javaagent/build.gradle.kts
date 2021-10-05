plugins {
  id("otel.javaagent-instrumentation")
}

// TODO: add muzzle once 1.4.0 is released

val versions: Map<String, String> by project

dependencies {
  compileOnly(project(":instrumentation-api-annotation-support"))

  compileOnly(project(":javaagent-tooling"))

  // this instrumentation needs to do similar shading dance as opentelemetry-api-1.0 because
  // the @WithSpan annotation references the OpenTelemetry API's SpanKind class
  //
  // see the comment in opentelemetry-api-1.0.gradle for more details
  compileOnly(project(path = ":opentelemetry-ext-annotations-shaded-for-instrumenting", configuration = "shadow"))

  testImplementation("io.opentelemetry:opentelemetry-extension-annotations")
  testImplementation(project(":instrumentation-api-annotation-support"))
  testImplementation("net.bytebuddy:byte-buddy:${versions["net.bytebuddy"]}")
}

tasks {
  compileTestJava {
    options.compilerArgs.add("-parameters")
  }
  test {
    jvmArgs("-Dotel.instrumentation.opentelemetry-annotations.exclude-methods=io.opentelemetry.test.annotation.TracedWithSpan[ignored]")
  }
}
