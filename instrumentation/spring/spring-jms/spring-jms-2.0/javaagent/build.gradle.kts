plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("org.springframework")
    module.set("spring-jms")
    versions.set("[2.0,6)")
    extraDependency("javax.jms:jms-api:1.1-rev-1")
    excludeInstrumentationName("jms")
    assertInverse.set(true)
  }
}

dependencies {
  bootstrap(project(":instrumentation:jms:jms-common:bootstrap"))
  implementation(project(":instrumentation:jms:jms-common:javaagent"))
  implementation(project(":instrumentation:jms:jms-1.1:javaagent"))
  library("org.springframework:spring-jms:2.0")
  compileOnly("javax.jms:jms-api:1.1-rev-1")

  compileOnly("com.google.auto.value:auto-value-annotations")
  annotationProcessor("com.google.auto.value:auto-value")

  testImplementation(project(":instrumentation:spring:spring-jms:spring-jms-2.0:testing"))
  testInstrumentation(project(":instrumentation:jms:jms-1.1:javaagent"))

  testImplementation("org.springframework.boot:spring-boot-starter-activemq:2.5.3")
  testImplementation("org.springframework.boot:spring-boot-starter-test:2.5.3") {
    exclude("org.junit.vintage", "junit-vintage-engine")
  }

  testImplementation("org.hornetq:hornetq-jms-client:2.4.7.Final")
  testImplementation("org.hornetq:hornetq-jms-server:2.4.7.Final") {
    // this doesn't exist in maven central, and doesn't seem to be needed anyways
    exclude("org.jboss.naming", "jnpserver")
  }

  latestDepTestLibrary("org.springframework:spring-jms:5.+") // see spring-jms-6.0 module
}

testing {
  suites {
    val testReceiveSpansDisabled by registering(JvmTestSuite::class) {
      dependencies {
        implementation(project(":instrumentation:spring:spring-jms:spring-jms-2.0:testing"))
        // this is just to avoid a bit more copy-pasting
        implementation(project.sourceSets["test"].output)
      }
    }
  }
}

configurations {
  named("testReceiveSpansDisabledImplementation") {
    extendsFrom(configurations["testImplementation"])
  }
}

tasks {
  withType<Test>().configureEach {
    usesService(gradle.sharedServices.registrations["testcontainersBuildService"].service)
  }
  // this does not apply to testReceiveSpansDisabled
  test {
    jvmArgs("-Dotel.instrumentation.messaging.experimental.receive-telemetry.enabled=true")
  }

  check {
    dependsOn(testing.suites)
  }
}
