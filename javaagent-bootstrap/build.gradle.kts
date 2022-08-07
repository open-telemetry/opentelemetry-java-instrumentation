plugins {
  id("otel.java-conventions")
  id("otel.publish-conventions")
}

group = "io.opentelemetry.javaagent"

dependencies {
  implementation(project(":instrumentation-api"))
  implementation(project(":instrumentation-appender-api-internal"))
  implementation("org.slf4j:slf4j-api")
  implementation("org.slf4j:slf4j-simple")

  testImplementation(project(":testing-common"))
}

tasks.withType<Test>().configureEach {
  // required on jdk17
  jvmArgs("--add-opens=java.base/java.lang=ALL-UNNAMED")
  jvmArgs("-XX:+IgnoreUnrecognizedVMOptions")
}
