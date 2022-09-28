plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("org.apache.rocketmq")
    module.set("rocketmq-client")
    versions.set("[4.0.0,)")
    assertInverse.set(true)
  }
}

dependencies {
  library("org.apache.rocketmq:rocketmq-client:4.8.0")

  implementation(project(":instrumentation:rocketmq:rocketmq-client:rocketmq-client-4.8:library"))

  testImplementation(project(":instrumentation:rocketmq:rocketmq-client:rocketmq-client-4.8:testing"))

  testLibrary("org.apache.rocketmq:rocketmq-test:4.8.0")
}

tasks.withType<Test>().configureEach {
  jvmArgs("-Dotel.instrumentation.rocketmq-client.experimental-span-attributes=true")

  systemProperty("testLatestDeps", findProperty("testLatestDeps") as Boolean)
}
