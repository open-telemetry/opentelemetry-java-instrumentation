plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
}

dependencies {

  library("com.google.cloud:google-cloud-pubsub:1.120.21")
}
