plugins {
  id("otel.library-instrumentation")
}

dependencies {
  library("io.projectreactor:reactor-core:3.1.0.RELEASE")
  implementation(project(":instrumentation-api-annotation-support"))
  testLibrary("io.projectreactor:reactor-test:3.1.0.RELEASE")

  testImplementation(project(":instrumentation:reactor:reactor-3.1:testing"))

  // Looks like later versions on reactor need this dependency for some reason even though it is marked as optional.
  latestDepTestLibrary("io.micrometer:micrometer-core:1.+")
}

tasks.withType<Test>().configureEach {
  // required on jdk17, uses spock Mock that needs access to jdk internals
  jvmArgs("--add-opens=java.base/java.lang.invoke=ALL-UNNAMED")
  jvmArgs("-XX:+IgnoreUnrecognizedVMOptions")
}
