plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("org.hibernate")
    module.set("hibernate-core")
    versions.set("[6.0.0.Final,)")
    assertInverse.set(true)
  }
}

dependencies {
  library("org.hibernate:hibernate-core:6.0.0.Final")

  implementation(project(":instrumentation:hibernate:hibernate-common:javaagent"))

  testInstrumentation(project(":instrumentation:jdbc:javaagent"))
  // Added to ensure cross compatibility:
  testInstrumentation(project(":instrumentation:hibernate:hibernate-3.3:javaagent"))
  testInstrumentation(project(":instrumentation:hibernate:hibernate-4.0:javaagent"))
  testInstrumentation(project(":instrumentation:hibernate:hibernate-procedure-call-4.3:javaagent"))

  testImplementation("com.h2database:h2:1.4.197")
  testImplementation("javax.xml.bind:jaxb-api:2.2.11")
  testImplementation("com.sun.xml.bind:jaxb-core:2.2.11")
  testImplementation("com.sun.xml.bind:jaxb-impl:2.2.11")
  testImplementation("javax.activation:activation:1.1.1")
  testImplementation("org.hsqldb:hsqldb:2.0.0")
  testImplementation("org.springframework.data:spring-data-jpa:3.0.0")
}

otelJava {
  minJavaVersionSupported.set(JavaVersion.VERSION_11)
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
