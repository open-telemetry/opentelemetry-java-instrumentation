import kotlin.math.max

plugins {
  id("otel.javaagent-testing")
}

dependencies {
  testInstrumentation(project(":instrumentation:executors:javaagent"))

  testCompileOnly(project(":instrumentation:executors:bootstrap"))
  testImplementation(project(":instrumentation:executors:testing"))
}

otelJava {
  // StructuredTaskScopeTest that uses preview feature, requires that the test is compiled for the
  // same vm version that is going to execute the test. Choose whichever is greater 21 or the
  // version of the vm that is going to run test
  val testJavaVersion =
    gradle.startParameter.projectProperties["testJavaVersion"]?.let(JavaVersion::toVersion)
      ?: JavaVersion.current()
  minJavaVersionSupported.set(JavaVersion.toVersion(max(
    testJavaVersion.majorVersion.toInt(),
    JavaVersion.VERSION_21.majorVersion.toInt()
  )))
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
