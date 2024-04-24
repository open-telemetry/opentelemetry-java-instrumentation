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

tasks.withType<Test>().configureEach {
  // needed for VirtualThreadTest
  jvmArgs("--add-opens=java.base/java.lang=ALL-UNNAMED")
  jvmArgs("-XX:+IgnoreUnrecognizedVMOptions")
}
