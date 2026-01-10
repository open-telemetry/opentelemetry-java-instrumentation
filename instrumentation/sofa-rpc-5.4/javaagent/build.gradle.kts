plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("com.alipay.sofa")
    module.set("sofa-rpc-all")
    versions.set("[5.4.0,)")
    assertInverse.set(true)
  }
}

dependencies {
  implementation(project(":instrumentation:sofa-rpc-5.4:library-autoconfigure"))

  library("com.alipay.sofa:sofa-rpc-all:5.4.0")
}

val latestDepTest = findProperty("testLatestDeps") as Boolean

testing {
  suites {
    // using a test suite to ensure that project(":instrumentation:sofa-rpc-5.4:library-autoconfigure")
    // is not available on test runtime class path, otherwise instrumentation from library-autoconfigure
    // module would be used instead of the javaagent instrumentation that we want to test
    val testSofaRpc by registering(JvmTestSuite::class) {
      dependencies {
        implementation(project(":instrumentation:sofa-rpc-5.4:testing"))
        if (latestDepTest) {
          implementation("com.alipay.sofa:sofa-rpc-all:latest.release")
        } else {
          implementation("com.alipay.sofa:sofa-rpc-all:5.4.0")
        }
        runtimeOnly("ch.qos.logback:logback-classic:1.2.13")
        runtimeOnly("ch.qos.logback:logback-core:1.2.13")
        runtimeOnly("org.slf4j:slf4j-api:1.7.21")
      }
    }
  }
}

tasks.withType<Test>().configureEach {
  systemProperty("testLatestDeps", findProperty("testLatestDeps") as Boolean)
  jvmArgs("-XX:+IgnoreUnrecognizedVMOptions")
  // to suppress non-fatal errors on jdk17
  jvmArgs("--add-opens=java.base/java.math=ALL-UNNAMED")
  // required on jdk17
  jvmArgs("--add-opens=java.base/java.lang=ALL-UNNAMED")

  systemProperty("collectMetadata", findProperty("collectMetadata")?.toString() ?: "false")
}

tasks {
  check {
    dependsOn(testing.suites)
  }

  if (findProperty("denyUnsafe") as Boolean) {
    // SOFA RPC's tracer module uses Disruptor which requires sun.misc.Unsafe.
    withType<Test>().configureEach {
      enabled = false
    }
  }
}

configurations.named("testSofaRpcRuntimeClasspath") {
  resolutionStrategy {
    // requires old logback (and therefore also old slf4j)
    force("ch.qos.logback:logback-classic:1.2.13")
    force("ch.qos.logback:logback-core:1.2.13")
    force("org.slf4j:slf4j-api:1.7.21")
  }
}
