plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("org.opensearch.client")
    module.set("opensearch-rest-client")
    versions.set("[1.0,)")
    assertInverse.set(true)
  }

  fail {
    group.set("org.opensearch.client")
    module.set("rest")
    versions.set("(,)")
  }
}

otelJava {
  minJavaVersionSupported.set(JavaVersion.VERSION_11)
}

dependencies {
  library("org.opensearch.client:opensearch-rest-client:1.0.0")

  implementation(project(":instrumentation:opensearch:opensearch-rest-common:javaagent"))

  testInstrumentation(project(":instrumentation:apache-httpclient:apache-httpclient-4.0:javaagent"))
  testInstrumentation(project(":instrumentation:apache-httpasyncclient-4.1:javaagent"))

  testImplementation("org.apache.logging.log4j:log4j-core:2.18.0")
  testImplementation("org.apache.logging.log4j:log4j-api:2.18.0")
  testImplementation("org.apache.commons:commons-lang3:3.12.0")
  testImplementation("commons-io:commons-io:2.11.0")
  testImplementation("org.opensearch:opensearch-testcontainers:2.0.0")
}

tasks {
  test {
    usesService(gradle.sharedServices.registrations["testcontainersBuildService"].service)
  }

  val testStableSemconv by registering(Test::class) {
    jvmArgs("-Dotel.semconv-stability.opt-in=database")
  }

  check {
    dependsOn(testStableSemconv)
  }
}
