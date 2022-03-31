plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    coreJdk()
  }
}

dependencies {
  compileOnly(project(":javaagent-tooling"))
  compileOnly(project(":instrumentation-api-annotation-support"))
}

tasks.withType<Test>().configureEach {
  jvmArgs("-Dotel.instrumentation.methods.include=io.opentelemetry.javaagent.instrumentation.methods.MethodTest\$ConfigTracedCallable[call]")
}
