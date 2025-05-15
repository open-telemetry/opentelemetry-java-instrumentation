plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("io.vertx")
    module.set("vertx-sql-client")
    versions.set("[4.0.0,5)")
    assertInverse.set(true)
  }
}

dependencies {
  val version = "4.0.0"
  library("io.vertx:vertx-sql-client:$version")
  compileOnly("io.vertx:vertx-codegen:$version")

  testInstrumentation(project(":instrumentation:netty:netty-4.1:javaagent"))

  testLibrary("io.vertx:vertx-pg-client:$version")
  testLibrary("io.vertx:vertx-codegen:$version")

  latestDepTestLibrary("io.vertx:vertx-sql-client:4.+")
  latestDepTestLibrary("io.vertx:vertx-pg-client:4.+")
  latestDepTestLibrary("io.vertx:vertx-codegen:4.+")
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
