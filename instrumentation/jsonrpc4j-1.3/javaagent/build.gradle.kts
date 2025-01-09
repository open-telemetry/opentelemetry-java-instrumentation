plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("com.github.briandilley.jsonrpc4j")
    module.set("jsonrpc4j")
    versions.set("[1.3.3,)")
    assertInverse.set(true)
  }
}

dependencies {
  implementation(project(":instrumentation:jsonrpc4j-1.3:library"))

  library("com.github.briandilley.jsonrpc4j:jsonrpc4j:1.3.3")

  testImplementation(project(":instrumentation:jsonrpc4j-1.3:testing"))
}

tasks {
  test {
    jvmArgs("-Dotel.javaagent.experimental.thread-propagation-debugger.enabled=false")
  }
}
