plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("org.mongodb")
    module.set("mongo-java-driver")
    versions.set("[3.7, 4.0)")
    assertInverse.set(true)
  }
  pass {
    group.set("org.mongodb")
    module.set("mongodb-driver-core")
    // this instrumentation is backwards compatible with early versions of the new API that shipped in 3.7
    // the legacy API instrumented in mongo-3.1 continues to be shipped in 4.x, but doesn't conflict here
    // because they are triggered by different types: MongoClientSettings(new) vs MongoClientOptions(legacy)
    versions.set("[3.7, 4.0)")
    assertInverse.set(true)
  }
}

dependencies {
  implementation(project(":instrumentation:mongo:mongo-3.1:library"))

  // a couple of test attribute verifications don't pass until 3.8.0
  library("org.mongodb:mongo-java-driver:3.8.0")

  testImplementation(project(":instrumentation:mongo:mongo-common:testing"))
}

tasks {
  test {
    usesService(gradle.sharedServices.registrations["testcontainersBuildService"].getService())
  }
}
