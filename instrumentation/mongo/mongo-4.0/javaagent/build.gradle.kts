plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("org.mongodb")
    module.set("mongodb-driver-core")
    versions.set("[4.0,)")
    assertInverse.set(true)
  }
}

dependencies {
  implementation(project(":instrumentation:mongo:mongo-3.1:library"))

  library("org.mongodb:mongodb-driver-core:4.0.0")

  testLibrary("org.mongodb:mongodb-driver-sync:4.0.0")
  testLibrary("org.mongodb:mongodb-driver-reactivestreams:4.0.0")

  testImplementation(project(":instrumentation:mongo:mongo-common:testing"))
  testImplementation("de.flapdoodle.embed:de.flapdoodle.embed.mongo:1.50.5")

  testInstrumentation(project(":instrumentation:mongo:mongo-async-3.3:javaagent"))
  testInstrumentation(project(":instrumentation:mongo:mongo-3.1:javaagent"))
  testInstrumentation(project(":instrumentation:mongo:mongo-3.7:javaagent"))
}

tasks {
  withType<Test>().configureEach {
    usesService(gradle.sharedServices.registrations["testcontainersBuildService"].service)
  }

  val testStableSemconv by registering(Test::class) {
    jvmArgs("-Dotel.semconv-stability.opt-in=database")
  }

  check {
    dependsOn(testStableSemconv)
  }
}
