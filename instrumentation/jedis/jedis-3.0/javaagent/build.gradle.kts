plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  fail {
    group.set("redis.clients")
    module.set("jedis")
    versions.set("[,3.0.0)")
    // TODO: remove this once jedis people release the new version correctly
    skip("jedis-3.6.2")
  }

  pass {
    group.set("redis.clients")
    module.set("jedis")
    versions.set("[3.0.0,)")
  }
}

dependencies {
  library("redis.clients:jedis:3.0.0")

  compileOnly("com.google.auto.value:auto-value-annotations")
  annotationProcessor("com.google.auto.value:auto-value")

  // ensures jedis-1.4 instrumentation does not load with jedis 3.0+ by failing
  // the tests in the event it does. The tests will end up with double spans
  testInstrumentation(project(":instrumentation:jedis:jedis-1.4:javaagent"))

  testLibrary("redis.clients:jedis:3.+")
}

tasks {
  test {
    usesService(gradle.sharedServices.registrations["testcontainersBuildService"].getService())
  }
}
