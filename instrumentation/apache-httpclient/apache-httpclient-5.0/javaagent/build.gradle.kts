plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("org.apache.httpcomponents.client5")
    module.set("httpclient5")
    versions.set("[5.0,)")
  }
}

dependencies {
  library("org.apache.httpcomponents.client5:httpclient5:5.0")
  // https://issues.apache.org/jira/browse/HTTPCORE-653
  library("org.apache.httpcomponents.core5:httpcore5:5.0.3")
}

tasks {
  withType<Test>().configureEach {
    systemProperty("testLatestDeps", findProperty("testLatestDeps") as Boolean)
  }

  test {
    systemProperty("collectMetadata", findProperty("collectMetadata")?.toString() ?: "false")
  }
}
