plugins {
  id("otel.javaagent-instrumentation")
}

/*
Note: The Interceptor class for OkHttp was not introduced until 2.2+, so we need to make sure the
instrumentation is not loaded unless the dependency is 2.2+.
*/
muzzle {
  pass {
    group.set("com.squareup.okhttp")
    module.set("okhttp")
    versions.set("[2.2,3)")
    assertInverse.set(true)
  }
}

dependencies {
  bootstrap(project(":instrumentation:executors:bootstrap"))

  library("com.squareup.okhttp:okhttp:2.2.0")

  testInstrumentation(project(":instrumentation:okhttp:okhttp-3.0:javaagent"))

  latestDepTestLibrary("com.squareup.okhttp:okhttp:2.+") // see okhttp-3.0 module
}

tasks {
  test {
    systemProperty("collectMetadata", findProperty("collectMetadata")?.toString() ?: "false")
    systemProperty("otel.instrumentation.common.peer-service-mapping", "127.0.0.1=test-peer-service,localhost=test-peer-service,192.0.2.1=test-peer-service")
  }
}
