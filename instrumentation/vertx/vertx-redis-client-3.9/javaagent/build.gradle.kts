plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("io.vertx")
    module.set("vertx-redis-client")
    versions.set("[3.9.1,4.0.0)")
    assertInverse.set(true)
  }
  pass {
    group.set("io.vertx")
    module.set("vertx-redis-client")
    versions.set("[3.9.2,4.0.0)")
    assertInverse.set(true)
  }
  pass {
    group.set("io.vertx")
    module.set("vertx-redis-client")
    versions.set("[3.9.3,4.0.0)")
    assertInverse.set(true)
  }
  pass {
    group.set("io.vertx")
    module.set("vertx-redis-client")
    versions.set("[3.9.5,4.0.0)")
    assertInverse.set(true)
  }
}

dependencies {
  library("io.vertx:vertx-redis-client:3.9.1")
  compileOnly("io.vertx:vertx-codegen:3.9.1")

  testInstrumentation(project(":instrumentation:netty:netty-4.1:javaagent"))

  testLibrary("io.vertx:vertx-codegen:3.9.1")
  testLibrary("io.vertx:vertx-redis-client:3.9.2")
  testLibrary("io.vertx:vertx-redis-client:3.9.3")
  testLibrary("io.vertx:vertx-redis-client:3.9.5")
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
