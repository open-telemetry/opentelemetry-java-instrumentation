plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("redis.clients")
    module.set("jedis")
    versions.set("[4.0.0-beta1,)")
    skip("jedis-3.6.2")
    assertInverse.set(true)
  }
}

dependencies {
  library("redis.clients:jedis:4.0.0-beta1")

  compileOnly("com.google.auto.value:auto-value-annotations")
  annotationProcessor("com.google.auto.value:auto-value")

  testInstrumentation(project(":instrumentation:jedis:jedis-1.4:javaagent"))
  testInstrumentation(project(":instrumentation:jedis:jedis-3.0:javaagent"))
}

tasks {
  test {
    usesService(gradle.sharedServices.registrations["testcontainersBuildService"].getService())
  }
}
