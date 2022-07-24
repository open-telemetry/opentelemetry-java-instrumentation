plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
}

dependencies {

  library("com.google.cloud:google-cloud-pubsub:1.101.0")
}
