plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("io.vertx")
    module.set("vertx-sql-client")
    versions.set("[4.0.0,)")
    assertInverse.set(true)
  }
}

dependencies {
  library("io.vertx:vertx-sql-client:4.1.0")
  compileOnly("io.vertx:vertx-codegen:4.1.0")

  testLibrary("io.vertx:vertx-pg-client:4.1.0")
  testLibrary("io.vertx:vertx-codegen:4.1.0")
  testLibrary("io.vertx:vertx-opentelemetry:4.1.0")
}

tasks.withType<Test>().configureEach {
  usesService(gradle.sharedServices.registrations["testcontainersBuildService"].service)
}
