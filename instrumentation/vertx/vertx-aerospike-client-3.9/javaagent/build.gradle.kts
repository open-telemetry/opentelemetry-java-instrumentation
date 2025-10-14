plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("com.aerospike")
    module.set("aerospike-client")
    versions.set("[4.4.0,)")
    assertInverse.set(true)
  }
}

dependencies {
  // Aerospike client as a LIBRARY (this is what we're instrumenting)
  library("com.aerospike:aerospike-client:4.4.18")
  
  // Vert.x for async patterns
  library("io.vertx:vertx-core:3.9.0")
  compileOnly("io.vertx:vertx-codegen:3.9.0")

  testInstrumentation(project(":instrumentation:netty:netty-4.1:javaagent"))

  testLibrary("io.vertx:vertx-codegen:3.9.0")
  testLibrary("io.vertx:vertx-core:3.9.0")
  testLibrary("com.aerospike:aerospike-client:4.4.18")
}

val collectMetadata = findProperty("collectMetadata")?.toString() ?: "false"

tasks {
  withType<Test>().configureEach {
    usesService(gradle.sharedServices.registrations["testcontainersBuildService"].service)
    systemProperty("collectMetadata", collectMetadata)
  }

  val testStableSemconv by registering(Test::class) {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    jvmArgs("-Dotel.semconv-stability.opt-in=database")
    systemProperty("collectMetadata", collectMetadata)
    systemProperty("metadataConfig", "otel.semconv-stability.opt-in=database")
  }

  check {
    dependsOn(testStableSemconv)
  }
}

