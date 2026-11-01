plugins {
  id("otel.testcontainers-testing")
  id("otel.javaagent-instrumentation")
  id("otel.nullaway-conventions")
}

muzzle {
  pass {
    name.set("mongo-3.1")
    group.set("org.mongodb")
    module.set("mongo-java-driver")
    versions.set("[3.1,)")
    assertInverse.set(true)
    excludeInstrumentationModule("io.opentelemetry.javaagent.instrumentation.mongo.v3_1.Mongo37InstrumentationModule")
  }
  pass {
    // instrumentation-docs:ignore - verification only, the directive above is the range we document
    name.set("mongo-3.7")
    group.set("org.mongodb")
    module.set("mongo-java-driver")
    versions.set("[3.7,4.0)")
    assertInverse.set(true)
    excludeInstrumentationModule("io.opentelemetry.javaagent.instrumentation.mongo.v3_1.Mongo31InstrumentationModule")
  }
  pass {
    name.set("mongo-3.1-client-settings")
    group.set("org.mongodb")
    module.set("mongodb-driver-core")
    versions.set("[3.7,4.0)")
    assertInverse.set(true)
    excludeInstrumentationModule("io.opentelemetry.javaagent.instrumentation.mongo.v3_1.Mongo31InstrumentationModule")
  }
}

dependencies {
  implementation(project(":instrumentation:mongo:mongo-3.1:library"))

  library("org.mongodb:mongo-java-driver:3.1.0")
  compileOnly("org.mongodb:mongo-java-driver:3.7.0")
  latestDepTestLibrary("org.mongodb:mongo-java-driver:3.+") // see mongo-4.0 module

  testImplementation(project(":instrumentation:mongo:mongo-3.1:testing"))

  testInstrumentation(project(":instrumentation:mongo:mongo-async-3.3:javaagent"))
  testInstrumentation(project(":instrumentation:mongo:mongo-4.0:javaagent"))
}

testing {
  suites {
    register<JvmTestSuite>("version37Test") {
      dependencies {
        implementation("org.mongodb:mongo-java-driver:${baseVersion("3.7.0").orLatest("3.+")}")
        implementation(project(":instrumentation:mongo:mongo-3.1:testing"))
        implementation("com.github.jnr:jnr-unixsocket:0.18")
      }
    }
  }
}

tasks {
  withType<Test>().configureEach {
    systemProperty("testLatestDeps", otelProps.testLatestDeps)
  }

  check {
    dependsOn(testing.suites)
  }
}
