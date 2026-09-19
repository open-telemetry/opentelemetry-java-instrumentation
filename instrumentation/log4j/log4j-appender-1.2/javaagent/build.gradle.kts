plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("log4j")
    module.set("log4j")
    versions.set("[1.2,)")
    assertInverse.set(true)
  }
}

dependencies {
  // 1.2 introduces MDC and there's no version earlier than 1.2.4 available
  library("log4j:log4j:1.2.4")

  testInstrumentation(project(":instrumentation:log4j:log4j-appender-2.17:javaagent"))
}

configurations {
  // In order to test the real log4j library we need to remove the log4j transitive
  // dependency 'log4j-over-slf4j' brought in by :testing-common which would shadow
  // the log4j module under test using a proxy to slf4j instead.
  testImplementation {
    exclude("org.slf4j", "log4j-over-slf4j")
  }
}

tasks.withType<Test>().configureEach {
  // TODO run tests both with and without experimental log attributes
  jvmArgs("-Dotel.instrumentation.log4j-appender.experimental.capture-code-attributes=true")
  jvmArgs("-Dotel.instrumentation.log4j-appender.experimental-log-attributes=true")
}

tasks {
  test {
    jvmArgs(
      "-Dotel.instrumentation.log4j-appender.experimental.mdc-attributes.included=key1,key2,exact,prefix.*,single?,excluded*,otel.event.name",
      "-Dotel.instrumentation.log4j-appender.experimental.mdc-attributes.excluded=prefix.secret,excluded*",
    )
  }

  val testStableSemconv = register<Test>("testStableSemconv") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath

    jvmArgs(
      "-Dotel.semconv-stability.opt-in=code",
      "-Dotel.instrumentation.log4j-appender.experimental.mdc-attributes.included=key1,key2,exact,prefix.*,single?,excluded*,otel.event.name",
      "-Dotel.instrumentation.log4j-appender.experimental.mdc-attributes.excluded=prefix.secret,excluded*",
    )
  }

  val testLegacyMdcAttributes = register<Test>("testLegacyMdcAttributes") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    filter.includeTestsMatching("*Log4jMdcSelectorTest")

    // the deprecated setting matches keys literally, so "*" alongside another entry does not
    // capture every key
    jvmArgs("-Dotel.instrumentation.log4j-appender.experimental.capture-mdc-attributes=*,legacy")
    systemProperty("testMdcConfiguration", "legacy")
  }

  val testMdcAttributeExclusionsOnly = register<Test>("testMdcAttributeExclusionsOnly") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    filter.includeTestsMatching("*Log4jMdcSelectorTest")

    jvmArgs("-Dotel.instrumentation.log4j-appender.experimental.mdc-attributes.excluded=prefix.secret")
    systemProperty("testMdcConfiguration", "exclude-only")
  }

  val testMdcAttributePrecedence = register<Test>("testMdcAttributePrecedence") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    filter.includeTestsMatching("*Log4jMdcSelectorTest")

    jvmArgs(
      "-Dotel.instrumentation.log4j-appender.experimental.mdc-attributes.included=new",
      "-Dotel.instrumentation.log4j-appender.experimental.capture-mdc-attributes=legacy",
    )
    systemProperty("testMdcConfiguration", "precedence")
  }

  check {
    dependsOn(
      testStableSemconv,
      testLegacyMdcAttributes,
      testMdcAttributeExclusionsOnly,
      testMdcAttributePrecedence,
    )
  }
}
