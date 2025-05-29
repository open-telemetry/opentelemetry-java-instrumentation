plugins {
  id("otel.library-instrumentation")
}

dependencies {
  library("io.ratpack:ratpack-core:1.7.0")

  testImplementation(project(":instrumentation:ratpack:ratpack-1.4:testing"))

  testLibrary("io.ratpack:ratpack-test:1.7.0")
  testLibrary("io.ratpack:ratpack-guice:1.7.0")

  if (JavaVersion.current().isCompatibleWith(JavaVersion.VERSION_11)) {
    testImplementation("com.sun.activation:jakarta.activation:1.2.2")
  }
}

tasks {
  withType<Test>().configureEach {
    // required on jdk17
    jvmArgs("--add-opens=java.base/java.lang=ALL-UNNAMED")
    jvmArgs("-XX:+IgnoreUnrecognizedVMOptions")
  }
}
