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

  testImplementation(project(":instrumentation:mongo:mongo-common:testing"))

  testInstrumentation(project(":instrumentation:mongo:mongo-3.1:javaagent"))
  testInstrumentation(project(":instrumentation:mongo:mongo-3.7:javaagent"))
  testInstrumentation(project(":instrumentation:mongo:mongo-4.0:javaagent"))
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
