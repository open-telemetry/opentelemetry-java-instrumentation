plugins {
  id("otel.javaagent-instrumentation")
  id("otel.nullaway-conventions")
}

muzzle {
  pass {
    group.set("com.google.cloud")
    module.set("spring-cloud-gcp-pubsub")
versions.set("[5.0.0,)")
    assertInverse.set(true)
  }
}

dependencies {
  library("com.google.cloud:spring-cloud-gcp-pubsub:5.0.0")

  testLibrary("com.google.cloud:spring-cloud-gcp-starter-pubsub:5.0.0")
  testLibrary("org.springframework.boot:spring-boot-starter:3.2.4")
  testLibrary("org.springframework.boot:spring-boot-starter-test:3.2.4")
  testLibrary("org.springframework.boot:spring-boot-starter-integration:3.2.4")

  testImplementation("org.testcontainers:testcontainers-gcloud")

  latestDepTestLibrary("com.google.cloud:spring-cloud-gcp-pubsub:5.+") // documented limitation
  latestDepTestLibrary("com.google.cloud:spring-cloud-gcp-starter-pubsub:5.+") // documented limitation
  latestDepTestLibrary("org.springframework.boot:spring-boot-starter:3.+") // documented limitation
  latestDepTestLibrary("org.springframework.boot:spring-boot-starter-test:3.+") // documented limitation
  latestDepTestLibrary("org.springframework.boot:spring-boot-starter-integration:3.+") // documented limitation
}

otelJava {
  minJavaVersionSupported.set(JavaVersion.VERSION_17)
}

tasks {
  withType<Test>().configureEach {
    // the grpc and spring-integration instrumentations emit spans that make the traces in this
    // test non-deterministic (Pub/Sub ack executor RPCs and the channel interceptor), so they are
    // disabled here; the process spans under test are unaffected
    jvmArgs("-Dotel.instrumentation.grpc.enabled=false")
    jvmArgs("-Dotel.instrumentation.spring-integration.enabled=false")
    usesService(gradle.sharedServices.registrations["testcontainersBuildService"].service)
    systemProperty("collectMetadata", otelProps.collectMetadata)
  }

  val testMessagingPreview = register<Test>("testMessagingPreview") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    jvmArgs("-Dotel.semconv-stability.preview=messaging")
    systemProperty("metadataConfig", "otel.semconv-stability.preview=messaging")
  }

  val testBothSemconv = register<Test>("testBothSemconv") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    jvmArgs("-Dotel.semconv-stability.preview=messaging/dup")
    systemProperty("metadataConfig", "otel.semconv-stability.preview=messaging/dup")
  }

  check {
    dependsOn(testMessagingPreview, testBothSemconv)
  }

  if (otelProps.denyUnsafe) {
    // the gRPC/GAX stack backing the Pub/Sub emulator used in the tests uses sun.misc.Unsafe
    withType<Test>().configureEach {
      enabled = false
    }
  }
}
