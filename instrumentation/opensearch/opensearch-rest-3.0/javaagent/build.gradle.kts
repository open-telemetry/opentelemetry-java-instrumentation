plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("org.opensearch.client")
    module.set("opensearch-rest-client")
    versions.set("[3.0,)")
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
  library("org.opensearch.client:opensearch-rest-client:3.0.0")

  implementation(project(":instrumentation:opensearch:opensearch-rest-common:javaagent"))

  testInstrumentation(project(":instrumentation:opensearch:opensearch-rest-1.0:javaagent"))
  testInstrumentation(project(":instrumentation:apache-httpclient:apache-httpclient-5.0:javaagent"))

  testImplementation(project(":instrumentation:opensearch:opensearch-rest-common:testing"))
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
