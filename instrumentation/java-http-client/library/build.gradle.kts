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
  val testStableSemconv by registering(Test::class) {
    jvmArgs("-Dotel.semconv-stability.opt-in=http")
  }

  check {
    dependsOn(testStableSemconv)
  }
}
