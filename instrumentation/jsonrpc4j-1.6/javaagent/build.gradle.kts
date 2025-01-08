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

val jsonrpcVersion = "1.6"

dependencies {
  implementation(project(":instrumentation:jsonrpc4j-1.6:library"))
  implementation("com.github.briandilley.jsonrpc4j:jsonrpc4j:$jsonrpcVersion")
  testImplementation(project(":instrumentation:jsonrpc4j-1.6:testing"))
}

tasks {
  test {
    systemProperty("testLatestDeps", findProperty("testLatestDeps") as Boolean)
    jvmArgs("-Dotel.javaagent.experimental.thread-propagation-debugger.enabled=false")
    jvmArgs("-Dotel.instrumentation.common.experimental.controller-telemetry.enabled=true")
  }
}
