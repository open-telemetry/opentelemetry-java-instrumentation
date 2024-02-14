plugins {
  id("otel.library-instrumentation")
}

dependencies {
  library("com.google.cloud:google-cloud-pubsub:1.126.6")
}
