plugins {
  id("otel.library-instrumentation")
  id("otel.nullaway-conventions")
}

otelJava {
  minJavaVersionSupported.set(JavaVersion.VERSION_11)
}

dependencies {
  testImplementation(project(":instrumentation:java-http-client:testing"))
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
