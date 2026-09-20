plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("javax.jms")
    module.set("javax.jms-api")
    versions.set("[2.0,)")
    assertInverse.set(true)
  }
  pass {
    group.set("jakarta.jms")
    module.set("jakarta.jms-api")
    versions.set("[2.0,3)")
    assertInverse.set(true)
  }
}

dependencies {
  implementation(project(":instrumentation:jms:jms-common-1.1:javaagent"))
  // for JavaxMessageAdapter / JavaxDestinationAdapter, which are shared with the classic API
  implementation(project(":instrumentation:jms:jms-1.1:javaagent"))

  compileOnly("javax.jms:javax.jms-api:2.0.1")

  testInstrumentation(project(":instrumentation:jms:jms-1.1:javaagent"))
  testInstrumentation(project(":instrumentation:jms:jms-3.0:javaagent"))

  // HornetQ 2.4.7 is a JMS 2.0 provider and runs in-VM, so these tests need no broker container
  testImplementation("org.hornetq:hornetq-jms-client:2.4.7.Final")
  testImplementation("org.hornetq:hornetq-jms-server:2.4.7.Final")
}

tasks {
  val testMessagingPreview = register<Test>("testMessagingPreview") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    jvmArgs("-Dotel.instrumentation.messaging.experimental.receive-telemetry.enabled=true")
    jvmArgs("-Dotel.semconv-stability.preview=messaging")
  }

  test {
    jvmArgs("-Dotel.instrumentation.messaging.experimental.receive-telemetry.enabled=true")
  }

  check {
    dependsOn(testMessagingPreview)
  }
}

configurations.configureEach {
  // this doesn't exist in maven central, and doesn't seem to be needed anyways
  // included from org.hornetq:hornetq-jms-server:2.4.7.Final
  exclude("org.jboss.naming", "jnpserver")
}
