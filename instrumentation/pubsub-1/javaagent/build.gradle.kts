plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("com.google.cloud")
    module.set("google-cloud-pubsub")
    versions.set("[1.120.4,)")
    assertInverse.set(true)
  }
}

dependencies {
  library("com.google.cloud:google-cloud-pubsub:1.126.6")
  implementation(project(":instrumentation:pubsub-1:library"))

  compileOnly("com.google.auto.value:auto-value-annotations")
  annotationProcessor("com.google.auto.value:auto-value")

  testImplementation("org.testcontainers:gcloud")
}

tasks.withType<Test>().configureEach {
  usesService(gradle.sharedServices.registrations["testcontainersBuildService"].service)
}
