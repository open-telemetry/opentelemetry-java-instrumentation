plugins {
  id("otel.javaagent-testing")
}

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
}

otelJava {
  minJavaVersionSupported.set(JavaVersion.VERSION_17)
}

tasks.withType<Test>().configureEach {
  // TODO run tests both with and without experimental span attributes
  jvmArgs("-Dotel.instrumentation.hibernate.experimental-span-attributes=true")
}
