plugins {
  id("otel.javaagent-instrumentation")
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

dependencies {
  implementation(project(":instrumentation:jms:jms-common:javaagent"))

  compileOnly("javax.jms:jms-api:1.1-rev-1")

  testImplementation("org.apache.activemq:activemq-client:5.16.5")
}

testing {
  suites {
    val jms2Test by registering(JvmTestSuite::class) {
      dependencies {
        implementation("org.hornetq:hornetq-jms-client:2.4.7.Final")
        implementation("org.hornetq:hornetq-jms-server:2.4.7.Final")
      }

      targets {
        all {
          testTask.configure {
            jvmArgs("-Dotel.instrumentation.messaging.experimental.receive-telemetry.enabled=true")
          }
        }
      }
    }
  }
}

tasks {
  withType<Test>().configureEach {
    usesService(gradle.sharedServices.registrations["testcontainersBuildService"].service)
  }

  val testReceiveSpansDisabled by registering(Test::class) {
    filter {
      includeTestsMatching("Jms1SuppressReceiveSpansTest")
    }
    include("**/Jms1SuppressReceiveSpansTest.*")
  }

  test {
    filter {
      excludeTestsMatching("Jms1SuppressReceiveSpansTest")
    }
    jvmArgs("-Dotel.instrumentation.messaging.experimental.receive-telemetry.enabled=true")
  }

  check {
    dependsOn(testing.suites)
    dependsOn(testReceiveSpansDisabled)
  }
}

configurations.configureEach {
  // this doesn't exist in maven central, and doesn't seem to be needed anyways
  // included from org.hornetq:hornetq-jms-server:2.4.7.Final
  exclude("org.jboss.naming", "jnpserver")
}
