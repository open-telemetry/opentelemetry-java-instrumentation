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
}

tasks {
  val jms2Test by existing(Test::class) {
    filter {
      // this is needed because "test.dependsOn jms2Test", and so without this,
      // running a single test in the default test set will fail
      setFailOnNoMatchingTests(false)
    }
  }

  named<Test>("test") {
    dependsOn(jms2Test)
    usesService(gradle.sharedServices.registrations["testcontainersBuildService"].getService())
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
}
