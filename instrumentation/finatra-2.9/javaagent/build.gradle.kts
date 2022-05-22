plugins {
  id("otel.javaagent-instrumentation")
  id("otel.scala-conventions")
  id("org.unbroken-dome.test-sets")
}

testSets {
  // We need separate test sources to compile against latest Finatra.
  create("latestDepTest")
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

dependencies {
  // TODO(anuraaga): Something about library configuration doesn't work well with scala compilation
  // here.
  compileOnly("com.twitter:finatra-http_2.11:2.9.0")

  testInstrumentation(project(":instrumentation:netty:netty-4.1:javaagent"))

  if (!(findProperty("testLatestDeps") as Boolean)) {
    // Requires old version of Jackson
    testImplementation(enforcedPlatform("com.fasterxml.jackson:jackson-bom:2.9.10"))
  }
  testImplementation("com.twitter:finatra-http_2.11:19.12.0") {
    // Finatra POM references linux-aarch64 version of this which we don't need. Including it
    // prevents us from managing Netty version because the classifier name changed to linux-aarch_64
    // in recent releases. So we exclude and force the linux-x86_64 classifier instead.
    exclude("io.netty", "netty-transport-native-epoll")
  }
  testImplementation("io.netty:netty-transport-native-epoll:4.1.51.Final:linux-x86_64")
  // Required for older versions of finatra on JDKs >= 11
  testImplementation("com.sun.activation:javax.activation:1.2.0")

  add("latestDepTestImplementation", "com.twitter:finatra-http_2.13:+") {
    exclude("io.netty", "netty-transport-native-epoll")
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
  }
}

if (findProperty("testLatestDeps") as Boolean) {
  configurations {
    // finatra artifact name is different for regular and latest tests
    testImplementation {
      exclude("com.twitter", "finatra-http_2.11")
    }
  }
}
