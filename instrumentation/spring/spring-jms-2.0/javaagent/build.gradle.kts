plugins {
  id("otel.javaagent-instrumentation")
  id("org.unbroken-dome.test-sets")
}

muzzle {
  pass {
    group.set("org.springframework")
    module.set("spring-jms")
    versions.set("[2.0,)")
    extraDependency("javax.jms:jms-api:1.1-rev-1")
    assertInverse.set(true)
  }
}

testSets {
  create("testReceiveSpansDisabled")
}

tasks {
  test {
    usesService(gradle.sharedServices.registrations["testcontainersBuildService"].service)
    jvmArgs("-Dotel.instrumentation.messaging.experimental.receive-telemetry.enabled=true")
  }

  val testReceiveSpansDisabled by existing

  check {
    dependsOn(testReceiveSpansDisabled)
  }
}

dependencies {
  implementation(project(":instrumentation:jms-1.1:javaagent"))
  library("org.springframework:spring-jms:2.0")
  compileOnly("javax.jms:jms-api:1.1-rev-1")

  compileOnly("com.google.auto.value:auto-value-annotations")
  annotationProcessor("com.google.auto.value:auto-value")

  testInstrumentation(project(":instrumentation:jms-1.1:javaagent"))

  testImplementation("org.springframework.boot:spring-boot-starter-activemq:2.5.3")
  testImplementation("org.springframework.boot:spring-boot-starter-test:2.5.3") {
    exclude("org.junit.vintage", "junit-vintage-engine")
  }

  testImplementation("org.hornetq:hornetq-jms-client:2.4.7.Final")
  testImplementation("org.hornetq:hornetq-jms-server:2.4.7.Final") {
    // this doesn't exist in maven central, and doesn't seem to be needed anyways
    exclude("org.jboss.naming", "jnpserver")
  }

  latestDepTestLibrary("org.springframework:spring-jms:5.+")

  // this is just to avoid a bit more copy-pasting
  add("testReceiveSpansDisabledImplementation", sourceSets["test"].output)
}
