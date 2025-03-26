plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("log4j")
    module.set("log4j")
    versions.set("[1.2,)")
    // version 1.2.15 has a bad dependency on javax.jms:jms:1.1 which was released as pom only
    skip("1.2.15")
    assertInverse.set(true)
  }
}

dependencies {
  // 1.2 introduces MDC and there's no version earlier than 1.2.4 available
  library("log4j:log4j:1.2.4")

  compileOnly(project(":javaagent-bootstrap"))

  testImplementation("org.awaitility:awaitility")
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
  jvmArgs("-Dotel.instrumentation.log4j-appender.experimental.capture-mdc-attributes=*")
  jvmArgs("-Dotel.instrumentation.log4j-appender.experimental-log-attributes=true")
  jvmArgs("-Dotel.instrumentation.log4j-appender.experimental-log-attributes=true")
  jvmArgs("-Dotel.instrumentation.common.experimental.controller-telemetry.enabled=true")
}
