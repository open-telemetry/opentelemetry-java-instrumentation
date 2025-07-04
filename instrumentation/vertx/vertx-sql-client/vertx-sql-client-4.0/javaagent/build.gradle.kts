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
  library("io.vertx:vertx-codegen:$version")

  implementation(project(":instrumentation:vertx:vertx-sql-client:vertx-sql-client-common:javaagent"))

  testInstrumentation(project(":instrumentation:netty:netty-4.1:javaagent"))
  testInstrumentation(project(":instrumentation:vertx:vertx-sql-client:vertx-sql-client-5.0:javaagent"))

  testLibrary("io.vertx:vertx-pg-client:$version")

  latestDepTestLibrary("io.vertx:vertx-sql-client:4.+") // see vertx-sql-client-5.0 module
  latestDepTestLibrary("io.vertx:vertx-pg-client:4.+") // see vertx-sql-client-5.0 module
  latestDepTestLibrary("io.vertx:vertx-codegen:4.+") // see vertx-sql-client-5.0 module
}

val collectMetadata = findProperty("collectMetadata")?.toString() ?: "false"

tasks {
  withType<Test>().configureEach {
    usesService(gradle.sharedServices.registrations["testcontainersBuildService"].service)
    systemProperty("collectMetadata", collectMetadata)
  }

  val testStableSemconv by registering(Test::class) {
    jvmArgs("-Dotel.semconv-stability.opt-in=database")

    systemProperty("metaDataConfig", "otel.semconv-stability.opt-in=database")
  }

  check {
    dependsOn(testStableSemconv)
  }
}

val latestDepTest = findProperty("testLatestDeps") as Boolean
if (!latestDepTest) {
  // https://bugs.openjdk.org/browse/JDK-8320431
  otelJava {
    maxJavaVersionForTests.set(JavaVersion.VERSION_21)
  }
}
