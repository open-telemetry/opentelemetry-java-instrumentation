plugins {
  id("otel.library-instrumentation")
}

dependencies {
  library("dev.failsafe:failsafe:3.0.1")
}

tasks.test {
  systemProperty("collectMetadata", findProperty("collectMetadata")?.toString() ?: "false")
}
