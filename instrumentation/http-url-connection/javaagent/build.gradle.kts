plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    coreJdk()
  }
}

tasks {
  val testStableSemconv by registering(Test::class) {
    jvmArgs("-Dotel.semconv-stability.opt-in=http")
  }

  check {
    dependsOn(testStableSemconv)
  }
}
