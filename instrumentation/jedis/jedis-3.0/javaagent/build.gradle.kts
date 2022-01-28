plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("redis.clients")
    module.set("jedis")
    versions.set("[3.0.0,4)")
    skip("jedis-3.6.2")
    assertInverse.set(true)
  }
}

dependencies {
  library("redis.clients:jedis:3.0.0")

  compileOnly("com.google.auto.value:auto-value-annotations")
  annotationProcessor("com.google.auto.value:auto-value")

  // ensures jedis-1.4 instrumentation does not load with jedis 3.0+ by failing
  // the tests in the event it does. The tests will end up with double spans
  testInstrumentation(project(":instrumentation:jedis:jedis-1.4:javaagent"))
  testInstrumentation(project(":instrumentation:jedis:jedis-4.0:javaagent"))

  latestDepTestLibrary("redis.clients:jedis:3.+") // see jedis-4.0 module
}

tasks {
  test {
    usesService(gradle.sharedServices.registrations["testcontainersBuildService"].getService())
  }
}
