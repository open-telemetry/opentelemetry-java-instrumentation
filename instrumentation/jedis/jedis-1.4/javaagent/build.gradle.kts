plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("redis.clients")
    module.set("jedis")
    versions.set("[1.4.0,3.0.0)")
    assertInverse.set(true)
  }
}

dependencies {
  library("redis.clients:jedis:1.4.0")

  compileOnly("com.google.auto.value:auto-value-annotations")
  annotationProcessor("com.google.auto.value:auto-value")

  // Jedis 3.0 has API changes that prevent instrumentation from applying
  latestDepTestLibrary("redis.clients:jedis:2.+")
}

tasks {
  named<Test>("test") {
    usesService(gradle.sharedServices.registrations["testcontainersBuildService"].getService())
  }
}
