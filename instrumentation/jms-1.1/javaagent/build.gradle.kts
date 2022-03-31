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
}

testSets {
  create("jms2Test")
  create("jms2TestReceiveSpansDisabled") {
    extendsFrom("jms2Test")
  }
}

tasks {
  val testReceiveSpansDisabled by registering(Test::class) {
    filter {
      includeTestsMatching("SpringListenerJms1SuppressReceiveSpansTest")
    }
    include("**/SpringListenerJms1SuppressReceiveSpansTest.*")
  }

  val jms2Test by existing(Test::class) {
    jvmArgs("-Dotel.instrumentation.messaging.experimental.receive-telemetry.enabled=true")
  }

  val jms2TestReceiveSpansDisabled by existing

  test {
    usesService(gradle.sharedServices.registrations["testcontainersBuildService"].getService())
    filter {
      excludeTestsMatching("SpringListenerJms1SuppressReceiveSpansTest")
    }
    jvmArgs("-Dotel.instrumentation.messaging.experimental.receive-telemetry.enabled=true")
  }

  check {
    dependsOn(testReceiveSpansDisabled)
    dependsOn(jms2Test)
    dependsOn(jms2TestReceiveSpansDisabled)
  }
}

val versions: Map<String, String> by project

dependencies {
  compileOnly("com.google.auto.value:auto-value-annotations")
  annotationProcessor("com.google.auto.value:auto-value")

  compileOnly("javax.jms:jms-api:1.1-rev-1")

  testImplementation("javax.annotation:javax.annotation-api:1.3.2")
  testImplementation("org.springframework.boot:spring-boot-starter-activemq:${versions["org.springframework.boot"]}")
  testImplementation("org.springframework.boot:spring-boot-starter-test:${versions["org.springframework.boot"]}") {
    exclude("org.junit.vintage", "junit-vintage-engine")
  }

  add("jms2TestImplementation", "org.hornetq:hornetq-jms-client:2.4.7.Final")
  add("jms2TestImplementation", "org.hornetq:hornetq-jms-server:2.4.7.Final") {
    // this doesn't exist in maven central, and doesn't seem to be needed anyways
    exclude("org.jboss.naming", "jnpserver")
  }

  // this is just to avoid a bit more copy-pasting
  add("jms2TestReceiveSpansDisabledImplementation", sourceSets["jms2Test"].output)
}
