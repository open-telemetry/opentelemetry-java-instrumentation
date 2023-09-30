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
  }
}

dependencies {
  // 1.2 introduces MDC and there's no version earlier than 1.2.4 available
  library("log4j:log4j:1.2.4")
}

configurations {
  // In order to test the real log4j library we need to remove the log4j transitive
  // dependency 'log4j-over-slf4j' brought in by :testing-common which would shadow
  // the log4j module under test using a proxy to slf4j instead.
  testImplementation {
    exclude("org.slf4j", "log4j-over-slf4j")
  }

  // See: https://stackoverflow.com/a/9047963/2749853
  testImplementation {
    exclude("javax.jms", "jms")
  }
}
