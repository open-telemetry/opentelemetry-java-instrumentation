plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("org.apache.thrift")
    module.set("libthrift")
    versions.set("[0.9.1,)")
  }
}

dependencies {
  compileOnly("org.apache.thrift:libthrift:0.9.1")
  implementation(project(":instrumentation:thrift:thrift-common:library"))

  testImplementation("org.apache.thrift:libthrift:0.9.1")
  testImplementation("javax.annotation:javax.annotation-api:1.3.2")
}
