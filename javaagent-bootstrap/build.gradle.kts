plugins {
  id("otel.java-conventions")
  id("otel.publish-conventions")
}

group = "io.opentelemetry.javaagent"

val agentSlf4jVersion = "2.0.0"

dependencies {
  implementation(project(":instrumentation-api"))
  implementation(project(":instrumentation-appender-api-internal"))
  implementation("org.slf4j:slf4j-api:$agentSlf4jVersion")
  implementation("org.slf4j:slf4j-simple:$agentSlf4jVersion")

  testImplementation(project(":testing-common"))
}

tasks.withType<Test>().configureEach {
  // required on jdk17
  jvmArgs("--add-opens=java.base/java.lang=ALL-UNNAMED")
  jvmArgs("-XX:+IgnoreUnrecognizedVMOptions")
}
