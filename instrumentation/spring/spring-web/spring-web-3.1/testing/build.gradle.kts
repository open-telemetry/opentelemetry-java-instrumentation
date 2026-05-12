plugins {
  id("otel.javaagent-testing")
}

dependencies {
  library("org.springframework:spring-web:3.1.0.RELEASE")

  testInstrumentation(project(":instrumentation:http-url-connection:javaagent"))
  testInstrumentation(project(":instrumentation:spring:spring-web:spring-web-6.0:javaagent"))

  latestDepTestLibrary("org.springframework:spring-web:5.+") // see spring-web-6.0 module
}

tasks {
  val testStableSemconv by registering(Test::class) {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    jvmArgs("-Dotel.semconv-stability.opt-in=service.peer")
  }

  check {
    dependsOn(testStableSemconv)
  }
}
