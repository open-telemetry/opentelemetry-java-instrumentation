plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("org.elasticsearch.client")
    module.set("elasticsearch-rest-client")
    versions.set("[6.4,7.0)")
    assertInverse.set(true)
  }

  fail {
    group.set("org.elasticsearch.client")
    module.set("rest")
    versions.set("(,)")
  }
}

val versions: Map<String, String> by project

dependencies {
  library("org.elasticsearch.client:elasticsearch-rest-client:6.4.0")

  implementation(project(":instrumentation:elasticsearch:elasticsearch-rest-common:library"))

  testInstrumentation(project(":instrumentation:apache-httpclient:apache-httpclient-4.0:javaagent"))
  testInstrumentation(project(":instrumentation:apache-httpasyncclient-4.1:javaagent"))
  // TODO: review the following claim, we are not using embedded ES anymore
  // Netty is used, but it adds complexity to the tests since we're using embedded ES.
  // testInstrumentation(project(":instrumentation:netty:netty-4.1:javaagent"))

  testImplementation("org.apache.logging.log4j:log4j-core:2.11.0")
  testImplementation("org.apache.logging.log4j:log4j-api:2.11.0")

  testImplementation("org.testcontainers:elasticsearch:${versions["org.testcontainers"]}")
  testLibrary("org.elasticsearch.client:elasticsearch-rest-client:6.4.0")

  latestDepTestLibrary("org.elasticsearch.client:elasticsearch-rest-client:6.+")
}

tasks {
  named<Test>("test") {
    usesService(gradle.sharedServices.registrations["testcontainersBuildService"].getService())
  }
}
