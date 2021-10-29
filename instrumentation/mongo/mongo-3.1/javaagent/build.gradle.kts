plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("org.mongodb")
    module.set("mongo-java-driver")
    versions.set("[3.1,)")
    assertInverse.set(true)
  }
}

dependencies {
  implementation(project(":instrumentation:mongo:mongo-3.1:library"))

  library("org.mongodb:mongo-java-driver:3.1.0")

  testImplementation(project(":instrumentation:mongo:mongo-3.1:testing"))

  testInstrumentation(project(":instrumentation:mongo:mongo-async-3.3:javaagent"))
  testInstrumentation(project(":instrumentation:mongo:mongo-3.7:javaagent"))
  testInstrumentation(project(":instrumentation:mongo:mongo-4.0:javaagent"))
}

tasks {
  test {
    usesService(gradle.sharedServices.registrations["testcontainersBuildService"].getService())
  }
}
