plugins {
  id("otel.java-conventions")
  id("otel.publish-conventions")
}

group = "io.opentelemetry.javaagent"

dependencies {
  implementation(project(":instrumentation-api"))
  implementation("org.slf4j:slf4j-api")
  implementation("com.google.code.findbugs:jsr305:3.0.2")

  testImplementation(project(":testing-common"))
  testImplementation("org.mockito:mockito-core")
  testImplementation("org.assertj:assertj-core")

  // this only exists to make Intellij happy since it doesn't (currently at least) understand our
  // inclusion of this artifact inside of :instrumentation-api
  compileOnly(project(":instrumentation-api-caching"))
}
