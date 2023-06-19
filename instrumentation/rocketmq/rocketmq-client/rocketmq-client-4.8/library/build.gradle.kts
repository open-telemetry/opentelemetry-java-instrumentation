plugins {
  id("otel.library-instrumentation")
}

dependencies {
  library("org.apache.rocketmq:rocketmq-client:4.8.0")

  compileOnly("com.google.auto.value:auto-value-annotations")
  annotationProcessor("com.google.auto.value:auto-value")

  testLibrary("org.apache.rocketmq:rocketmq-test:4.8.0")

  testImplementation(project(":instrumentation:rocketmq:rocketmq-client:rocketmq-client-4.8:testing"))
}

tasks.withType<Test>().configureEach {
  systemProperty("testLatestDeps", findProperty("testLatestDeps") as Boolean)
  // required on jdk17
  jvmArgs("--add-opens=java.base/sun.nio.ch=ALL-UNNAMED")
  jvmArgs("-XX:+IgnoreUnrecognizedVMOptions")
}
