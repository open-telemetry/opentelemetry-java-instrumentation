plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("org.apache.kafka")
    module.set("connect-api")
    versions.set("[2.6.0,)")
    // WorkerSinkTask is in connect-runtime, but it's bundled with connect-api
    extraDependency("org.apache.kafka:connect-runtime:2.6.0")
  }
}

dependencies {
  bootstrap(project(":instrumentation:kafka:kafka-clients:kafka-clients-0.11:bootstrap"))
  implementation(project(":instrumentation:kafka:kafka-clients:kafka-clients-common-0.11:library"))
  library("org.apache.kafka:connect-api:2.6.0")
}
