plugins {
  id("otel.javaagent-testing")
}

val springAgent by configurations.creating

dependencies {
  library("org.hibernate:hibernate-core:6.0.0.Final")

  testInstrumentation(project(":instrumentation:hibernate:hibernate-6.0:javaagent"))
  testInstrumentation(project(":instrumentation:jdbc:javaagent"))
  // Added to ensure cross compatibility:
  testInstrumentation(project(":instrumentation:hibernate:hibernate-3.3:javaagent"))
  testInstrumentation(project(":instrumentation:hibernate:hibernate-4.0:javaagent"))
  testInstrumentation(project(":instrumentation:hibernate:hibernate-procedure-call-4.3:javaagent"))

  testImplementation("org.hsqldb:hsqldb:2.0.0")
  testImplementation("org.springframework.data:spring-data-jpa:3.0.0")

  springAgent("org.springframework:spring-instrument:6.0.7")
}

otelJava {
  minJavaVersionSupported.set(JavaVersion.VERSION_17)
}

tasks {
  withType<Test>().configureEach {
    jvmArgs("-javaagent:" + springAgent.singleFile.absolutePath)

    // TODO run tests both with and without experimental span attributes
    jvmArgs("-Dotel.instrumentation.hibernate.experimental-span-attributes=true")

    jvmArgs("-Dotel.instrumentation.common.experimental.controller-telemetry.enabled=true")
  }

  val testStableSemconv by registering(Test::class) {
    jvmArgs("-Dotel.semconv-stability.opt-in=database")
  }

  check {
    dependsOn(testStableSemconv)
  }
}
