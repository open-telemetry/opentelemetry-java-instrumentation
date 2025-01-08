plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("com.github.briandilley.jsonrpc4j")
    module.set("jsonrpc4j")
    versions.set("[1.6,)")
    assertInverse.set(true)
  }
}

dependencies {
  implementation(project(":instrumentation:jsonrpc4j-1.6:library"))

  library("com.github.briandilley.jsonrpc4j:jsonrpc4j:1.6")

  testImplementation(project(":instrumentation:jsonrpc4j-1.6:testing"))
}

tasks {
  test {
    jvmArgs("-Dotel.javaagent.experimental.thread-propagation-debugger.enabled=false")
  }
}
