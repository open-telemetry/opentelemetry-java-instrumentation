plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("org.apache.thrift")
    module.set("libthrift")
    versions.set("[0.9.3,)")
    assertInverse.set(true)
  }
}

dependencies {
  library("org.apache.thrift:libthrift:0.9.3")
  implementation(project(":instrumentation:thrift:thrift-common:javaagent"))
  implementation(project(":instrumentation:thrift:thrift-0.9.1:javaagent"))

  testImplementation("org.apache.thrift:libthrift:0.9.1")
  testImplementation("javax.annotation:javax.annotation-api:1.3.2")
}
