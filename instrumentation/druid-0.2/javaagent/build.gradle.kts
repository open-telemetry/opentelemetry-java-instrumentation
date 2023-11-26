plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("com.alibaba")
    module.set("druid")
    versions.set("[0.2.6,)")
  }
}

dependencies {
  library("com.alibaba:druid:0.2.6")

  implementation(project(":instrumentation:druid-0.2:library"))

  testImplementation(project(":instrumentation:druid-0.2:testing"))
}
