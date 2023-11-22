plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("com.alibaba")
    module.set("druid")
    versions.set("[1.1.22,)")
    assertInverse.set(true)
  }
}

dependencies {
  library("com.alibaba:druid:1.2.20")

  implementation(project(":instrumentation:druid-1.1:library"))

  testImplementation(project(":instrumentation:druid-1.1:testing"))
}
