plugins {
  id("otel.javaagent-instrumentation")
  id("org.unbroken-dome.test-sets")
}

muzzle {
  pass {
    group.set("javax.jms")
    module.set("jms-api")
    versions.set("(,)")
  }
  pass {
    group.set("javax.jms")
    module.set("javax.jms-api")
    versions.set("(,)")
  }
  pass {
    group.set("jakarta.jms")
    module.set("jakarta.jms-api")
    versions.set("(,3)")
    assertInverse.set(true)
  }
}

testSets {
  create("jms2Test")
}

tasks {
  val jms2Test by existing(Test::class) {
    jvmArgs("-Dotel.instrumentation.messaging.experimental.receive-telemetry.enabled=true")
  }

  test {
    usesService(gradle.sharedServices.registrations["testcontainersBuildService"].service)
    jvmArgs("-Dotel.instrumentation.messaging.experimental.receive-telemetry.enabled=true")
  }

  check {
    dependsOn(jms2Test)
  }
}

dependencies {
  implementation(project(":instrumentation:jms:jms-common:javaagent"))

  compileOnly("javax.jms:jms-api:1.1-rev-1")

  testImplementation("org.apache.activemq:activemq-client:5.16.5")

  add("jms2TestImplementation", "org.hornetq:hornetq-jms-client:2.4.7.Final")
  add("jms2TestImplementation", "org.hornetq:hornetq-jms-server:2.4.7.Final") {
    // this doesn't exist in maven central, and doesn't seem to be needed anyways
    exclude("org.jboss.naming", "jnpserver")
  }
}
