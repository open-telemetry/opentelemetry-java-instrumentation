plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("ch.qos.logback")
    module.set("logback-classic")
    versions.set("[0.9.16,)")
    assertInverse.set(true)
  }
}

dependencies {
  // pin the version strictly to avoid overriding by dependencyManagement versions
  compileOnly("ch.qos.logback:logback-classic") {
    version {
      strictly("1.0.0")
    }
  }
  compileOnly("org.slf4j:slf4j-api") {
    version {
      strictly("1.5.8")
    }
  }

  if (otelProps.testLatestDeps) {
    testImplementation("ch.qos.logback:logback-classic:latest.release")
  } else {
    testImplementation("ch.qos.logback:logback-classic") {
      version {
        strictly("1.0.0")
      }
    }
    testImplementation("org.slf4j:slf4j-api") {
      version {
        strictly("1.7.36")
      }
    }
  }

  implementation(project(":instrumentation:logback:logback-appender-1.0:library"))

  testImplementation(project(":instrumentation:logback:logback-appender-1.0:testing"))
}

tasks {
  test {
    jvmArgs("-Dotel.instrumentation.logback-appender.experimental.mdc-attributes.included=key?")
  }

  val testMdcAttributeExclusionsOnly = register<Test>("testMdcAttributeExclusionsOnly") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    filter.includeTestsMatching("*LogbackMdcSelectorTest")

    jvmArgs("-Dotel.instrumentation.logback-appender.experimental.mdc-attributes.excluded=request-secret")
    systemProperty("testMdcConfiguration", "exclude-only")
  }

  val testLegacyMdcAttributes = register<Test>("testLegacyMdcAttributes") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    filter.includeTestsMatching("*LogbackMdcSelectorTest")

    // the deprecated setting matches keys literally, so "*" alongside another entry does not
    // capture every key
    jvmArgs("-Dotel.instrumentation.logback-appender.experimental.capture-mdc-attributes=*,legacy")
    systemProperty("testMdcConfiguration", "legacy")
  }

  val testLegacyMdcAttributesCaptureAll = register<Test>("testLegacyMdcAttributesCaptureAll") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    filter.includeTestsMatching("*LogbackMdcSelectorTest")

    jvmArgs("-Dotel.instrumentation.logback-appender.experimental.capture-mdc-attributes=*")
    systemProperty("testMdcConfiguration", "legacy-all")
  }

  val testMdcAttributePrecedence = register<Test>("testMdcAttributePrecedence") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    filter.includeTestsMatching("*LogbackMdcSelectorTest")

    jvmArgs(
      "-Dotel.instrumentation.logback-appender.experimental.mdc-attributes.included=new",
      "-Dotel.instrumentation.logback-appender.experimental.capture-mdc-attributes=legacy",
    )
    systemProperty("testMdcConfiguration", "precedence")
  }

  check {
    dependsOn(
      testMdcAttributeExclusionsOnly,
      testLegacyMdcAttributes,
      testLegacyMdcAttributesCaptureAll,
      testMdcAttributePrecedence,
    )
  }
}

tasks.withType<Test>().configureEach {
  // TODO run tests both with and without experimental log attributes
  jvmArgs("-Dotel.instrumentation.logback-appender.experimental-log-attributes=true")
  jvmArgs("-Dotel.instrumentation.logback-appender.experimental.capture-code-attributes=true")
  jvmArgs("-Dotel.instrumentation.logback-appender.experimental.capture-marker-attribute=true")
}
