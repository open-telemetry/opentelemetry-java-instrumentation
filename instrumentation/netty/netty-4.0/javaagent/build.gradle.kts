plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("io.netty")
    module.set("netty-codec-http")
    versions.set("[4.0.0.Final,4.1.0.Final)")
    assertInverse.set(true)
  }
  pass {
    group.set("io.netty")
    module.set("netty-all")
    versions.set("[4.0.0.Final,4.1.0.Final)")
    excludeDependency("io.netty:netty-tcnative")
    assertInverse.set(true)
  }
  fail {
    group.set("io.netty")
    module.set("netty")
    versions.set("[,]")
  }
}

dependencies {
  library("io.netty:netty-codec-http:4.0.0.Final")
  implementation(project(":instrumentation:netty:netty-4-common:javaagent"))
  latestDepTestLibrary("io.netty:netty-codec-http:4.0.56.Final")
}

tasks {
  val testConnectionSpan by registering(Test::class) {
    filter {
      includeTestsMatching("Netty40ConnectionSpanTest")
      isFailOnNoMatchingTests = false
    }
    include("**/Netty40ConnectionSpanTest.*")
    jvmArgs("-Dotel.instrumentation.netty.always-create-connect-span=true")
  }

  test {
    dependsOn(testConnectionSpan)
    filter {
      excludeTestsMatching("Netty40ConnectionSpanTest")
      isFailOnNoMatchingTests = false
    }
  }
}

// We need to force the dependency to the earliest supported version because other libraries declare newer versions.
if (!(findProperty("testLatestDeps") as Boolean)) {
  configurations.configureEach {
    if (!name.contains("muzzle")) {
      resolutionStrategy {
        eachDependency {
          // specifying a fixed version for all libraries with io.netty' group
          if (requested.group == "io.netty" && requested.name != "netty-bom") {
            useVersion("4.0.0.Final")
          }
        }
      }
    }
  }
}
