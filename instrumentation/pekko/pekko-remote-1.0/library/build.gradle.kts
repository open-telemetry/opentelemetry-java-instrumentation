plugins {
  id("otel.library-instrumentation")
}

dependencies {
  library("org.apache.pekko:pekko-remote_2.12:1.0.1")
}
