plugins {
  id("otel.java-conventions")
  id("otel.japicmp-conventions")
  id("otel.publish-conventions")
}

group = "io.opentelemetry.javaagent"

dependencies {
  api("io.opentelemetry:opentelemetry-sdk")
  api("net.bytebuddy:byte-buddy-dep")

  implementation(project(":instrumentation-api"))
  implementation(project(":javaagent-instrumentation-api"))
  implementation("org.slf4j:slf4j-api")
  implementation("com.google.code.findbugs:jsr305:3.0.2")

  // metrics are unstable, do not expose as api
  implementation("io.opentelemetry:opentelemetry-sdk-metrics")

  // this only exists to make Intellij happy since it doesn't (currently at least) understand our
  // inclusion of this artifact inside of :instrumentation-api
  compileOnly(project(":instrumentation-api-caching"))
}
