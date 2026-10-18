plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("net.spy")
    module.set("spymemcached")
    versions.set("[2.12.0,)")
    assertInverse.set(true)
  }
}

val protocolStatusTestRuntimeClasspath by configurations.creating {
  isCanBeConsumed = false
  extendsFrom(configurations.testRuntimeClasspath.get())
}

dependencies {
  library("net.spy:spymemcached:2.12.0")

  compileOnly("com.google.auto.value:auto-value-annotations")
  annotationProcessor("com.google.auto.value:auto-value")

  testImplementation("com.google.guava:guava")
  add(protocolStatusTestRuntimeClasspath.name, "net.spy:spymemcached:2.12.3")
}

tasks {
  withType<Test>().configureEach {
    usesService(gradle.sharedServices.registrations["testcontainersBuildService"].service)
  }

  val testExperimental = register<Test>("testExperimental") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath

    jvmArgs("-Dotel.instrumentation.spymemcached.experimental-span-attributes=true")
    systemProperty("metadataConfig", "otel.instrumentation.spymemcached.experimental-span-attributes=true")
  }

  val testProtocolStatus = register<Test>("testProtocolStatus") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath =
      files(sourceSets.main.get().output, sourceSets.test.get().output, protocolStatusTestRuntimeClasspath)
    systemProperty("testLatestDeps", true)
    filter {
      includeTestsMatching("*SpymemcachedTest.storeRejectedAsTooLarge")
      includeTestsMatching("*SpymemcachedTest.conditionalOutcomesAreSuccessful")
      includeTestsMatching("*SpymemcachedTest.set")
      includeTestsMatching("*SpymemcachedTest.setCancel")
      includeTestsMatching("*SpymemcachedTest.casNotFound")
      includeTestsMatching("*SpymemcachedTest.getMiss")
    }
  }

  check {
    dependsOn(testExperimental, testProtocolStatus)
  }
}
