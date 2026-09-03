plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("redis.clients")
    module.set("jedis")
    versions.set("[4.0.0-beta1,)")
    assertInverse.set(true)
  }
}

dependencies {
  library("redis.clients:jedis:4.0.0-beta1")

  compileOnly("com.google.auto.value:auto-value-annotations")
  annotationProcessor("com.google.auto.value:auto-value")

  implementation(project(":instrumentation:jedis:jedis-common-1.4:javaagent"))

  testInstrumentation(project(":instrumentation:jedis:jedis-1.4:javaagent"))
  testInstrumentation(project(":instrumentation:jedis:jedis-2.0:javaagent"))
  testInstrumentation(project(":instrumentation:jedis:jedis-3.0:javaagent"))
}

tasks {
  withType<Test>().configureEach {
    // latest dep test fails because peer ip is 0:0:0:0:0:0:0:1 instead of 127.0.0.1
    jvmArgs("-Djava.net.preferIPv4Stack=true")
    usesService(gradle.sharedServices.registrations["testcontainersBuildService"].service)
    systemProperty("collectMetadata", otelProps.collectMetadata)
  }

  val testStableSemconv = register<Test>("testStableSemconv") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath

    jvmArgs("-Dotel.semconv-stability.opt-in=database")
    systemProperty("metadataConfig", "otel.semconv-stability.opt-in=database")
  }

  val testBothSemconv = register<Test>("testBothSemconv") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    filter {
      includeTestsMatching("*Jedis40ClientTest.setCommand")
      includeTestsMatching("*Jedis40ClientTest.pooledCommand")
    }

    jvmArgs("-Dotel.semconv-stability.opt-in=database/dup")
    systemProperty("metadataConfig", "otel.semconv-stability.opt-in=database/dup")
  }

  check {
    dependsOn(testBothSemconv, testStableSemconv)
  }
}
