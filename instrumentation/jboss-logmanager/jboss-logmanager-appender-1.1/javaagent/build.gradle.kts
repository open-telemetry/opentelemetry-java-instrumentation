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

tasks {
  withType<Test>().configureEach {
    // TODO run tests both with and without experimental log attributes
    jvmArgs("-Dotel.instrumentation.jboss-logmanager.experimental-log-attributes=true")
    jvmArgs("-Dotel.instrumentation.java-util-logging.experimental-log-attributes=true")
  }

  test {
    jvmArgs(
      "-Dotel.instrumentation.jboss-logmanager.experimental.mdc-attributes.included=exact,prefix.*,single?,excluded*,otel.event.name",
      "-Dotel.instrumentation.jboss-logmanager.experimental.mdc-attributes.excluded=prefix.secret,excluded*",
    )
    systemProperty("testMdcConfiguration", "new")
  }

  val testCaptureTemplateAndArguments = register<Test>("testCaptureTemplateAndArguments") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath

    jvmArgs(
      "-Dotel.instrumentation.jboss-logmanager.experimental.capture-template=true",
      "-Dotel.instrumentation.jboss-logmanager.experimental.capture-arguments=true",
    )
  }

  val testLegacyMdcAttributes = register<Test>("testLegacyMdcAttributes") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath

    jvmArgs("-Dotel.instrumentation.jboss-logmanager.experimental.capture-mdc-attributes=legacy")
    systemProperty("testMdcConfiguration", "legacy")
  }

  val testMdcAttributePrecedence = register<Test>("testMdcAttributePrecedence") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath

    jvmArgs(
      "-Dotel.instrumentation.jboss-logmanager.experimental.mdc-attributes.included=new",
      "-Dotel.instrumentation.jboss-logmanager.experimental.capture-mdc-attributes=legacy",
    )
    systemProperty("testMdcConfiguration", "precedence")
  }

  val testExcludedOnlyMdcAttributes = register<Test>("testExcludedOnlyMdcAttributes") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath

    filter {
      includeTestsMatching("JbossLogmanagerTest.testMdc")
    }

    jvmArgs("-Dotel.instrumentation.jboss-logmanager.experimental.mdc-attributes.excluded=prefix.secret,excluded*")
    systemProperty("testMdcConfiguration", "excludedOnly")
  }

  check {
    dependsOn(
      testCaptureTemplateAndArguments,
      testLegacyMdcAttributes,
      testMdcAttributePrecedence,
      testExcludedOnlyMdcAttributes,
    )
  }
}
