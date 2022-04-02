plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("org.jboss.logmanager")
    module.set("jboss-logmanager")
    versions.set("[2.1,)")
  }
}

dependencies {
  library("org.jboss.logmanager:jboss-logmanager:2.1.17.Final")

  compileOnly(project(":instrumentation-appender-api-internal"))

  // ensure no cross interference
  testInstrumentation(project(":instrumentation:java-util-logging:javaagent"))

  testImplementation("org.awaitility:awaitility")
}

tasks.withType<Test>().configureEach {
  // TODO run tests both with and without experimental log attributes
  jvmArgs("-Dotel.instrumentation.jboss-logmanager.experimental.capture-mdc-attributes=*")
  jvmArgs("-Dotel.instrumentation.jboss-logmanager.experimental-log-attributes=true")
}
