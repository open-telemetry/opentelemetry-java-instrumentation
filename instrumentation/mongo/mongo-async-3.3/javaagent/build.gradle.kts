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

  // Starting with 3.7.0, mongodb-driver-async depends on mongodb-driver-core which contains
  // both sync (com.mongodb.MongoClientSettings) and async (com.mongodb.async.SingleResultCallback)
  // classes, causing the mongo-3.7 instrumentation to match instead of mongo-async-3.3.
  latestDepTestLibrary("org.mongodb:mongodb-driver-async:3.6.+")

  testImplementation(project(":instrumentation:mongo:mongo-common:testing"))

  testInstrumentation(project(":instrumentation:mongo:mongo-3.1:javaagent"))
  testInstrumentation(project(":instrumentation:mongo:mongo-3.7:javaagent"))
  testInstrumentation(project(":instrumentation:mongo:mongo-4.0:javaagent"))
}

tasks {
  withType<Test>().configureEach {
    usesService(gradle.sharedServices.registrations["testcontainersBuildService"].service)
    systemProperty("collectMetadata", findProperty("collectMetadata")?.toString() ?: "false")
  }

  val testStableSemconv by registering(Test::class) {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    jvmArgs("-Dotel.semconv-stability.opt-in=database")
    systemProperty("metadataConfig", "otel.semconv-stability.opt-in=database")
  }

  check {
    dependsOn(testStableSemconv)
  }
}
