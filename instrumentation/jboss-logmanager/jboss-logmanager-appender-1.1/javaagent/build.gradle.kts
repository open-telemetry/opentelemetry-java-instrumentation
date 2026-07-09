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
}

dependencies {
  library("org.jboss.logmanager:jboss-logmanager:1.1.0.GA")

  // ensure no cross interference
  testInstrumentation(project(":instrumentation:java-util-logging:javaagent"))
}

if (otelProps.testLatestDeps) {
  otelJava {
    minJavaVersionSupported.set(JavaVersion.VERSION_11)
  }
}

tasks.test {
  // TODO run tests both with and without experimental log attributes
  jvmArgs("-Dotel.instrumentation.jboss-logmanager.experimental.capture-mdc-attributes=*")
  jvmArgs("-Dotel.instrumentation.jboss-logmanager.experimental-log-attributes=true")
  jvmArgs("-Dotel.instrumentation.java-util-logging.experimental-log-attributes=true")
}

tasks {
  val testCaptureTemplateAndArguments = register<Test>("testCaptureTemplateAndArguments") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath

    jvmArgs(
      "-Dotel.instrumentation.jboss-logmanager.experimental.capture-mdc-attributes=*",
      "-Dotel.instrumentation.jboss-logmanager.experimental-log-attributes=true",
      "-Dotel.instrumentation.java-util-logging.experimental-log-attributes=true",
      "-Dotel.instrumentation.jboss-logmanager.experimental.capture-template=true",
      "-Dotel.instrumentation.jboss-logmanager.experimental.capture-arguments=true",
    )
  }

  check {
    dependsOn(testCaptureTemplateAndArguments)
  }
}
