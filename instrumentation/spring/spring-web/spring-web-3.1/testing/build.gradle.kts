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
  val testPreviewSemconv = register<Test>("testPreviewSemconv") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    jvmArgs("-Dotel.semconv-stability.preview=service.peer")
  }

  check {
    dependsOn(testPreviewSemconv)
  }
}
