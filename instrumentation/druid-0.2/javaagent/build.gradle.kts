plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("com.alibaba")
    module.set("druid")
    versions.set("[0.2.6,)")
    assertInverse.set(true)
  }
}

dependencies {
  library("com.alibaba:druid:1.2.20")

  implementation(project(":instrumentation:druid-0.2:library"))

  testImplementation(project(":instrumentation:druid-0.2:testing"))
}
