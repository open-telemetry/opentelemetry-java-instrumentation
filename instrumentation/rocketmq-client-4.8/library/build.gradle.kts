plugins {
  id("otel.library-instrumentation")
}

dependencies {
  library("org.apache.rocketmq:rocketmq-client:4.8.0")

  compileOnly("com.google.auto.value:auto-value-annotations")
  annotationProcessor("com.google.auto.value:auto-value")

  testLibrary("org.apache.rocketmq:rocketmq-test:4.8.0")
  testImplementation(project(":instrumentation:rocketmq-client-4.8:testing"))

  // 4.9.1 seems to have a bug which makes on of our tests fail
  latestDepTestLibrary("org.apache.rocketmq:rocketmq-client:4.9.0")
  latestDepTestLibrary("org.apache.rocketmq:rocketmq-test:4.9.0")
}
