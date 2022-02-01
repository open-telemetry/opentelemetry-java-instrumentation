plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    coreJdk()
  }
}

tasks.withType<Test>().configureEach {
  jvmArgs("-Dotel.instrumentation.executors.include=ExecutorInstrumentationTest\$CustomThreadPoolExecutor")
  jvmArgs("-Djava.awt.headless=true")
  // ExecutorInstrumentationTest tess internal JDK class instrumentation
  jvmArgs("--add-opens", "java.base/java.util.concurrent=ALL-UNNAMED")
  jvmArgs("-XX:+IgnoreUnrecognizedVMOptions")
}
