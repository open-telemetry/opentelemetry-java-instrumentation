plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("org.hibernate")
    module.set("hibernate-core")
    versions.set("[4.3.0.Final,)")
    assertInverse.set(true)
  }
}

dependencies {
  library("org.hibernate:hibernate-core:4.3.0.Final")

  implementation(project(":instrumentation:hibernate:hibernate-common:javaagent"))

  testInstrumentation(project(":instrumentation:jdbc:javaagent"))
  // Added to ensure cross compatibility:
  testInstrumentation(project(":instrumentation:hibernate:hibernate-3.3:javaagent"))
  testInstrumentation(project(":instrumentation:hibernate:hibernate-4.0:javaagent"))

  testLibrary("org.hibernate:hibernate-entitymanager:4.3.0.Final")

  testImplementation("org.hsqldb:hsqldb:2.0.0")
  testImplementation("javax.xml.bind:jaxb-api:2.3.1")
  testImplementation("org.glassfish.jaxb:jaxb-runtime:2.3.3")

  latestDepTestLibrary("org.hibernate:hibernate-core:5.+") // documented limitation
  latestDepTestLibrary("org.hibernate:hibernate-entitymanager:5.+") // documented limitation
}

tasks {
  withType<Test>().configureEach {
    // TODO run tests both with and without experimental span attributes
    jvmArgs("-Dotel.instrumentation.hibernate.experimental-span-attributes=true")
  }

  val testStableSemconv by registering(Test::class) {
    jvmArgs("-Dotel.semconv-stability.opt-in=database")
  }

  check {
    dependsOn(testStableSemconv)
  }
}
