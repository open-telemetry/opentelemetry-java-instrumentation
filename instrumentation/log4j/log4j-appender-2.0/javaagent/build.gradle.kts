plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("org.apache.logging.log4j")
    module.set("log4j-core")
    versions.set("(,)")
  }

  pass {
    group.set("org.apache.logging.log4j")
    module.set("log4j-api")
    versions.set("(,)")
  }
}

dependencies {
  library("org.apache.logging.log4j:log4j-core:2.0")

  compileOnly(project(":instrumentation-api-appender"))

  testImplementation("org.awaitility:awaitility")

  testImplementation("com.lmax:disruptor:3.4.2")
}

configurations {
  configurations {
    testImplementation {
      // In order to test the real log4j library we need to remove the log4j transitive
      // dependency "log4j-over-slf4j" brought in by :testing-common which would shadow
      // the log4j module under test using a proxy to slf4j instead.
      exclude("org.slf4j", "log4j-over-slf4j")
    }
  }
}

tasks {
  val testAsync by registering(Test::class) {
    jvmArgs("-DLog4j2.contextSelector=org.apache.logging.log4j.core.async.AsyncLoggerContextSelector")
  }

  named<Test>("test") {
    dependsOn(testAsync)
  }
}
