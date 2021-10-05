plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("org.mongodb")
    module.set("mongodb-driver-async")
    versions.set("[3.3,)")
    extraDependency("org.mongodb:mongo-java-driver")
    assertInverse.set(true)
  }
}

dependencies {
  implementation(project(":instrumentation:mongo:mongo-3.1:library"))

  library("org.mongodb:mongodb-driver-async:3.3.0")

  testImplementation(project(":instrumentation:mongo:mongo-testing"))

  testInstrumentation(project(":instrumentation:mongo:mongo-3.7:javaagent"))
}

tasks {
  test {
    usesService(gradle.sharedServices.registrations["testcontainersBuildService"].getService())
  }
}
