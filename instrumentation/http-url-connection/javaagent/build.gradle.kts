plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    coreJdk()
  }
}

tasks {
  test {
    systemProperty("collectMetadata", findProperty("collectMetadata")?.toString()?.toBoolean() ?: false)
  }
}
