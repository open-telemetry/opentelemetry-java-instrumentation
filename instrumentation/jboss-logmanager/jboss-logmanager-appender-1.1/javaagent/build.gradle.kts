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

  compileOnly(project(":javaagent-bootstrap"))

  // ensure no cross interference
  testInstrumentation(project(":instrumentation:java-util-logging:javaagent"))

  testImplementation("org.awaitility:awaitility")
}

val latestDepTest = findProperty("testLatestDeps") as Boolean

if (latestDepTest) {
  otelJava {
    minJavaVersionSupported.set(JavaVersion.VERSION_11)
  }
}

tasks.withType<Test>().configureEach {
  // TODO run tests both with and without experimental log attributes
  jvmArgs("-Dotel.instrumentation.jboss-logmanager.experimental.capture-mdc-attributes=*")
  jvmArgs("-Dotel.instrumentation.jboss-logmanager.experimental.capture-event-name=true")
  jvmArgs("-Dotel.instrumentation.jboss-logmanager.experimental-log-attributes=true")
  jvmArgs("-Dotel.instrumentation.java-util-logging.experimental-log-attributes=true")
}
