plugins {
  id("otel.javaagent-instrumentation")
  id("otel.scala-conventions")
}

muzzle {
  // There are some weird library issues below 2.9 so can't assert inverse
  pass {
    group.set("com.twitter")
    module.set("finatra-http_2.11")
    versions.set("[2.9.0,]")
    excludeDependency("io.netty:netty-transport-native-epoll")
  }

  pass {
    group.set("com.twitter")
    module.set("finatra-http_2.12")
    versions.set("[2.9.0,]")
    excludeDependency("io.netty:netty-transport-native-epoll")
  }
}

// Test suites don't allow excluding transitive dependencies. We use this configuration to declare
// dependency to latest finatra and exclude netty-transport-native-epoll.
val finatraLatest by configurations.creating {
  isCanBeConsumed = false
  isCanBeResolved = false
}

dependencies {
  // TODO: Something about library configuration doesn't work well with scala compilation here.
  compileOnly("com.twitter:finatra-http_2.11:2.9.0")

  testInstrumentation(project(":instrumentation:netty:netty-4.1:javaagent"))

  testImplementation(enforcedPlatform("com.fasterxml.jackson:jackson-bom:2.9.10"))
  testImplementation("com.twitter:finatra-http_2.11:19.12.0") {
    // Finatra POM references linux-aarch64 version of this which we don't need. Including it
    // prevents us from managing Netty version because the classifier name changed to linux-aarch_64
    // in recent releases. So we exclude and force the linux-x86_64 classifier instead.
    exclude("io.netty", "netty-transport-native-epoll")
  }
  testImplementation("io.netty:netty-transport-native-epoll:4.1.51.Final:linux-x86_64")
  // Required for older versions of finatra on JDKs >= 11
  testImplementation("com.sun.activation:javax.activation:1.2.0")

  finatraLatest("com.twitter:finatra-http_2.13:+") {
    exclude("io.netty", "netty-transport-native-epoll")
  }
}

testing {
  suites {
    val latestDepTest by registering(JvmTestSuite::class) {
      dependencies {
        // finatra is included via finatraLatest configuation
        implementation("io.netty:netty-transport-native-epoll:4.1.51.Final:linux-x86_64")
      }
    }
  }
}

configurations {
  named("latestDepTestImplementation") {
    extendsFrom(configurations["finatraLatest"])
  }
}

tasks {
  if (findProperty("testLatestDeps") as Boolean) {
    // Separate task
    named("test") {
      enabled = false
    }
    named("compileTestScala") {
      enabled = false
    }

    check {
      dependsOn(testing.suites)
    }
  }

  withType<Test>().configureEach {
    // required on jdk17
    jvmArgs("--add-opens=java.base/java.lang=ALL-UNNAMED")
    jvmArgs("-XX:+IgnoreUnrecognizedVMOptions")
    jvmArgs("-Dotel.instrumentation.common.experimental.controller-telemetry.enabled=true")
  }
}
