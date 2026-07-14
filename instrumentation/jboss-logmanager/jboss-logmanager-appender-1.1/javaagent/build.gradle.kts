plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("org.jboss.logmanager")
    module.set("jboss-logmanager")
    versions.set("[1.1.0.GA,)")
    assertInverse.set(true)
  }

  // Allow IncludeExcludePredicate from opentelemetry-sdk-common
  pass {
    group.set("io.opentelemetry")
    module.set("opentelemetry-sdk-common")
    versions.set("[1.59.0,)")
  }
}

dependencies {
  library("org.jboss.logmanager:jboss-logmanager:1.1.0.GA")

  // for IncludeExcludePredicate, used to filter captured MDC attributes
  implementation("io.opentelemetry:opentelemetry-sdk-common")

  // ensure no cross interference
  testInstrumentation(project(":instrumentation:java-util-logging:javaagent"))
}

if (otelProps.testLatestDeps) {
  otelJava {
    minJavaVersionSupported.set(JavaVersion.VERSION_11)
  }
}

tasks {
  withType<Test>().configureEach {
    // TODO run tests both with and without experimental log attributes
    jvmArgs("-Dotel.instrumentation.jboss-logmanager.experimental.capture-mdc-attributes=*")
    jvmArgs("-Dotel.instrumentation.jboss-logmanager.experimental.exclude-mdc-attributes=excludedKey")
    jvmArgs("-Dotel.instrumentation.jboss-logmanager.experimental-log-attributes=true")
    jvmArgs("-Dotel.instrumentation.java-util-logging.experimental-log-attributes=true")
  }

  val testCaptureTemplateAndArguments = register<Test>("testCaptureTemplateAndArguments") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath

    jvmArgs(
      "-Dotel.instrumentation.jboss-logmanager.experimental.capture-template=true",
      "-Dotel.instrumentation.jboss-logmanager.experimental.capture-arguments=true",
    )
  }

  check {
    dependsOn(testCaptureTemplateAndArguments)
  }
}
