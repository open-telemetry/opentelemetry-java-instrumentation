plugins {
  id("otel.library-instrumentation")
}

dependencies {
  compileOnly("com.google.auto.value:auto-value-annotations")
  annotationProcessor("com.google.auto.value:auto-value")

  library("org.apache.dubbo:dubbo:2.7.0")
}

tasks.withType<Test>().configureEach {
  jvmArgs("-XX:+IgnoreUnrecognizedVMOptions")
  jvmArgs("--add-opens=java.base/java.math=ALL-UNNAMED")
  jvmArgs("--add-opens=java.base/java.lang=ALL-UNNAMED")
}
