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

dependencies {
  library("net.spy:spymemcached:2.12.0")

  compileOnly("com.google.auto.value:auto-value-annotations")
  annotationProcessor("com.google.auto.value:auto-value")

  testImplementation("com.google.guava:guava")
}

testing {
  suites {
    register<JvmTestSuite>("unitTests") {
      dependencies {
        implementation(project())
        implementation("net.spy:spymemcached:2.12.0")
      }
    }
  }
}

tasks {
  withType<Test>().configureEach {
    usesService(gradle.sharedServices.registrations["testcontainersBuildService"].service)
    systemProperty("testLatestDeps", otelProps.testLatestDeps)
  }

  val testExperimental = register<Test>("testExperimental") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath

    jvmArgs("-Dotel.instrumentation.spymemcached.experimental-span-attributes=true")
    systemProperty("metadataConfig", "otel.instrumentation.spymemcached.experimental-span-attributes=true")
  }

  check {
    dependsOn(testing.suites, testExperimental)
  }
}
