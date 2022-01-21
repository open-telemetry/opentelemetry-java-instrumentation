plugins {
  id("otel.javaagent-instrumentation")
}

// We cannot use otelJava { minJavaVersionSupported.set(JavaVersion.VERSION_1_9) } because compiler
// will fail with -Xlint without providing an error message.
// We cannot use "--release" javac option because that will forbid calling methods added in jdk 9.
java {
  sourceCompatibility = JavaVersion.VERSION_1_8
  targetCompatibility = JavaVersion.VERSION_1_8
  toolchain {
    languageVersion.set(null as JavaLanguageVersion?)
  }
}

tasks {
  withType<JavaCompile>().configureEach {
    options.release.set(null as Int?)
  }
}
