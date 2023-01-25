plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("org.apache.logging.log4j")
    module.set("log4j-core")
    versions.set("[2.0,)")
    assertInverse.set(true)
  }
}

dependencies {
  library("org.apache.logging.log4j:log4j-core:2.17.0")

  compileOnly("io.opentelemetry:opentelemetry-api-logs")
  compileOnly(project(":javaagent-bootstrap"))

  implementation(project(":instrumentation:log4j:log4j-appender-2.17:library"))

  testImplementation("org.awaitility:awaitility")

  // this is needed for the async logging test
  testImplementation("com.lmax:disruptor:3.4.2")
}

tasks {
  test {
    filter {
      excludeTestsMatching("Slf4jToLog4j2Test")
    }
    exclude("**/Slf4jToLog4j2Test.*")
  }

  val testAsync by registering(Test::class) {
    filter {
      excludeTestsMatching("Slf4jToLog4j2Test")
    }
    jvmArgs("-DLog4j2.contextSelector=org.apache.logging.log4j.core.async.AsyncLoggerContextSelector")
  }

  val testSlf4j1ToLog4j2 by registering(Test::class) {
    filter {
      includeTestsMatching("Slf4jToLog4j2Test")
    }
    include("**/Slf4jToLog4j2Test.*")
    configurations {
      testImplementation {
        // need to exclude logback in order to test slf4j -> log4j2
        exclude(group = "ch.qos.logback", module = "logback-classic")
      }
    }
    dependencies {
      testImplementation("org.slf4j:slf4j-api") {
        version {
          strictly("1.5.8")
        }
      }
      testImplementation("org.apache.logging.log4j:log4j-slf4j-impl:2.17.0")
    }
  }

  val testSlf4j2ToLog4j2 by registering(Test::class) {
    filter {
      includeTestsMatching("Slf4jToLog4j2Test")
    }
    configurations {
      testImplementation {
        // need to exclude logback in order to test slf4j -> log4j2
        exclude(group = "ch.qos.logback", module = "logback-classic")
      }
    }
    dependencies {
      // 2.19.0 is the first version of log4j-slf4j2-impl
      testLibrary("org.apache.logging.log4j:log4j-core:2.19.0")
      testImplementation("org.apache.logging.log4j:log4j-slf4j2-impl:2.19.0")
    }
  }

  check {
    dependsOn(testAsync)
    dependsOn(testSlf4j1ToLog4j2)
    dependsOn(testSlf4j2ToLog4j2)
  }
}

tasks.withType<Test>().configureEach {
  // TODO run tests both with and without experimental log attributes
  jvmArgs("-Dotel.instrumentation.log4j-appender.experimental.capture-map-message-attributes=true")
  jvmArgs("-Dotel.instrumentation.log4j-appender.experimental.capture-context-data-attributes=*")
  jvmArgs("-Dotel.instrumentation.log4j-appender.experimental-log-attributes=true")
  jvmArgs("-Dotel.instrumentation.log4j-appender.experimental.capture-marker-attribute=true")
}
