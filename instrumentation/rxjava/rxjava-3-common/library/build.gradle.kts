plugins {
  id("otel.library-instrumentation")
}

dependencies {
  library("io.reactivex.rxjava3:rxjava:3.0.12")
  implementation(project(":instrumentation-api-annotation-support"))
}

tasks.withType<Test>().configureEach {
  // required on jdk17, uses spock Mock that needs access to jdk internals
  jvmArgs("--add-opens=java.base/java.lang.invoke=ALL-UNNAMED")
  jvmArgs("-XX:+IgnoreUnrecognizedVMOptions")
}
