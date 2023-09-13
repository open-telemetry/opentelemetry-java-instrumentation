plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("co.elastic.clients")
    module.set("elasticsearch-java")
    versions.set("[7.16,8.10)") // 8.10+ has native, on-by-default opentelemetry instrumentation
    assertInverse.set(true)
  }
}

dependencies {
  library("co.elastic.clients:elasticsearch-java:7.16.0")

  implementation(project(":instrumentation:elasticsearch:elasticsearch-rest-common:javaagent"))

  testInstrumentation(project(":instrumentation:elasticsearch:elasticsearch-rest-7.0:javaagent"))
  testInstrumentation(project(":instrumentation:apache-httpclient:apache-httpclient-4.0:javaagent"))
  testInstrumentation(project(":instrumentation:apache-httpasyncclient-4.1:javaagent"))

  testImplementation("com.fasterxml.jackson.core:jackson-databind:2.14.2")
  testImplementation("org.testcontainers:elasticsearch")

  // 8.10+ has native, on-by-default opentelemetry instrumentation
  latestDepTestLibrary("co.elastic.clients:elasticsearch-java:8.9.+")
}

tasks {
  test {
    usesService(gradle.sharedServices.registrations["testcontainersBuildService"].service)
  }
}
