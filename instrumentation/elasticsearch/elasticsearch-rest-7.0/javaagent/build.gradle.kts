plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("org.elasticsearch.client")
    module.set("elasticsearch-rest-client")
    versions.set("[7.0,)")
    assertInverse.set(true)
  }

  fail {
    group.set("org.elasticsearch.client")
    module.set("rest")
    versions.set("(,)")
  }
}

dependencies {
  library("org.elasticsearch.client:elasticsearch-rest-client:7.0.0")

  implementation(project(":instrumentation:elasticsearch:elasticsearch-rest-common:javaagent"))

  testInstrumentation(project(":instrumentation:apache-httpclient:apache-httpclient-4.0:javaagent"))
  testInstrumentation(project(":instrumentation:apache-httpasyncclient-4.1:javaagent"))
  // TODO: review the following claim, we are not using embedded ES anymore
  // Netty is used, but it adds complexity to the tests since we're using embedded ES.
  // testInstrumentation(project(":instrumentation:netty:netty-4.1:javaagent"))

  testImplementation("org.apache.logging.log4j:log4j-core:2.11.0")
  testImplementation("org.apache.logging.log4j:log4j-api:2.11.0")

  testImplementation("org.testcontainers:elasticsearch")
}

tasks {
  test {
    usesService(gradle.sharedServices.registrations["testcontainersBuildService"].getService())
  }
}
