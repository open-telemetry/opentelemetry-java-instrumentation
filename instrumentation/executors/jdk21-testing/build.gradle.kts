plugins {
  id("otel.javaagent-testing")
}

dependencies {
  testInstrumentation(project(":instrumentation:executors:javaagent"))

  testCompileOnly(project(":instrumentation:executors:bootstrap"))
  testImplementation(project(":instrumentation:executors:testing"))
}

otelJava {
  minJavaVersionSupported.set(JavaVersion.VERSION_21)
}

tasks.withType<JavaCompile>().configureEach {
  with(options) {
    compilerArgs.add("--enable-preview")
  }
}

tasks.withType<Test>().configureEach {
  // needed for VirtualThreadTest
  jvmArgs("--add-opens=java.base/java.lang=ALL-UNNAMED")
  jvmArgs("-XX:+IgnoreUnrecognizedVMOptions")
  // needed for structured concurrency test
  jvmArgs("--enable-preview")
}
