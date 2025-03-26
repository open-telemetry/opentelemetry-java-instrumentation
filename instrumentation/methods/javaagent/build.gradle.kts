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
  compileOnly(project(":instrumentation-annotations-support"))
}

tasks.withType<Test>().configureEach {
  jvmArgs(
    "-Dotel.instrumentation.methods.include=io.opentelemetry.javaagent.instrumentation.methods.MethodTest\$ConfigTracedCallable[call];io.opentelemetry.javaagent.instrumentation.methods.MethodTest\$ConfigTracedCompletableFuture[getResult];javax.naming.directory.InitialDirContext[search]"
  )
}
