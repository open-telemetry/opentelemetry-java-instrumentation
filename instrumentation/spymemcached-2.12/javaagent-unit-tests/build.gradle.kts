plugins {
  id("otel.java-conventions")
}

dependencies {
  testImplementation(project(":instrumentation:spymemcached-2.12:javaagent"))
  testImplementation("net.spy:spymemcached:2.12.0")
}
