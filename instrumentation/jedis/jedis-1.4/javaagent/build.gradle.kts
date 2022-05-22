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

  implementation(project(":instrumentation:jedis:jedis-common:javaagent"))

  testInstrumentation(project(":instrumentation:jedis:jedis-3.0:javaagent"))
  testInstrumentation(project(":instrumentation:jedis:jedis-4.0:javaagent"))

  latestDepTestLibrary("redis.clients:jedis:2.+") // see jedis-3.0 module
}

tasks {
  test {
    usesService(gradle.sharedServices.registrations["testcontainersBuildService"].getService())
  }
}
