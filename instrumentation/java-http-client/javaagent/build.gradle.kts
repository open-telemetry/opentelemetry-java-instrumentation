plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    coreJdk()
  }
}

otelJava {
  minJavaVersionSupported.set(JavaVersion.VERSION_11)
}

dependencies {
  implementation(project(":instrumentation:java-http-client:library"))
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
