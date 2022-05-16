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

  compileOnly(project(":instrumentation-appender-api-internal"))
  compileOnly(project(":javaagent-bootstrap"))

  implementation(project(":instrumentation:log4j:log4j-appender-2.17:library"))

  testImplementation("org.awaitility:awaitility")

  // this is needed for the async logging test
  testImplementation("com.lmax:disruptor:3.4.2")
}

tasks {
  val testAsync by registering(Test::class) {
    jvmArgs("-DLog4j2.contextSelector=org.apache.logging.log4j.core.async.AsyncLoggerContextSelector")
  }

  named<Test>("test") {
    dependsOn(testAsync)
  }
}

tasks.withType<Test>().configureEach {
  // TODO run tests both with and without experimental log attributes
  jvmArgs("-Dotel.instrumentation.log4j-appender.experimental.capture-map-message-attributes=true")
  jvmArgs("-Dotel.instrumentation.log4j-appender.experimental.capture-context-data-attributes=*")
  jvmArgs("-Dotel.instrumentation.log4j-appender.experimental-log-attributes=true")
}
