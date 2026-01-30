plugins {
  id("otel.library-instrumentation")
}

otelJava {
  minJavaVersionSupported.set(JavaVersion.VERSION_17)
}

dependencies {
  library("org.eclipse.jetty:jetty-client:12.0.0")

  testImplementation(project(":instrumentation:jetty-httpclient::jetty-httpclient-12.0:testing"))
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
