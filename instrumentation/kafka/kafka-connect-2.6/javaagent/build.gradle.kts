plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("org.apache.kafka")
    module.set("connect-api")
    versions.set("[2.6.0,)")
  }
}

dependencies {
  implementation(project(":instrumentation:kafka:kafka-clients:kafka-clients-common-0.11:library"))

  library("org.apache.kafka:connect-api:2.6.0")

  testImplementation("org.apache.kafka:connect-runtime:2.6.0")
}

tasks {
  withType<Test>().configureEach {
    usesService(gradle.sharedServices.registrations["testcontainersBuildService"].service)

    systemProperty("testLatestDeps", findProperty("testLatestDeps") as Boolean)
    systemProperty("junit.jupiter.execution.timeout.default", "5m")
  }
}
