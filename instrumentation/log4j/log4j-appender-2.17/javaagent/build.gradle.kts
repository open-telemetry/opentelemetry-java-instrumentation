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

val testLatestDeps = findProperty("testLatestDeps") as Boolean

dependencies {
  library("org.apache.logging.log4j:log4j-core:2.0")

  compileOnly(project(":javaagent-bootstrap"))

  implementation(project(":instrumentation:log4j:log4j-appender-2.17:library"))

  testImplementation("org.awaitility:awaitility")

  if (testLatestDeps) {
    // this dependency is needed for the slf4j->log4j test
    testImplementation("org.apache.logging.log4j:log4j-slf4j2-impl:2.+")
    testCompileOnly("biz.aQute.bnd:biz.aQute.bnd.annotation:7.0.0")
  } else {
    // log4j 2.17 doesn't have an slf4j2 bridge
    testImplementation("org.apache.logging.log4j:log4j-slf4j-impl:2.17.0")
    testImplementation("org.slf4j:slf4j-api") {
      version {
        strictly("1.7.36")
      }
    }
  }

  // this is needed for the async logging test
  testLibrary("com.lmax:disruptor:3.4.2")
}

tasks.withType<Test>().configureEach {
  systemProperty("testLatestDeps", testLatestDeps)
}

tasks {
  val testAsync by registering(Test::class) {
    jvmArgs("-DLog4j2.contextSelector=org.apache.logging.log4j.core.async.AsyncLoggerContextSelector")
  }

  check {
    dependsOn(testAsync)
  }
}

tasks.withType<Test>().configureEach {
  // TODO run tests both with and without experimental log attributes
  jvmArgs("-Dotel.instrumentation.log4j-appender.experimental-log-attributes=true")
  jvmArgs("-Dotel.instrumentation.log4j-appender.experimental.capture-code-attributes=true")
  jvmArgs("-Dotel.instrumentation.log4j-appender.experimental.capture-map-message-attributes=true")
  jvmArgs("-Dotel.instrumentation.log4j-appender.experimental.capture-mdc-attributes=*")
  jvmArgs("-Dotel.instrumentation.log4j-appender.experimental.capture-marker-attribute=true")
}

configurations {
  testImplementation {
    // this is needed for the slf4j->log4j test
    exclude("ch.qos.logback", "logback-classic")
  }
}
