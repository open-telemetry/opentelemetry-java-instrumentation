plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("org.apache.shardingsphere.elasticjob")
    module.set("elasticjob-lite-core")
    versions.set("[3.0.0,)")
    assertInverse.set(true)
  }
}

dependencies {
  library("org.apache.shardingsphere.elasticjob:elasticjob-lite-core:3.0.0")

  testImplementation("org.apache.curator:curator-test:5.1.0")
}

tasks {
  withType<Test>().configureEach {
    systemProperty("collectMetadata", findProperty("collectMetadata")?.toString() ?: "false")
  }

  val testExperimental by registering(Test::class) {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath

    jvmArgs("-Dotel.instrumentation.apache-elasticjob.experimental-span-attributes=true")
    systemProperty("metadataConfig", "otel.instrumentation.apache-elasticjob.experimental-span-attributes=true")
  }

  check {
    dependsOn(testExperimental)
  }
}
