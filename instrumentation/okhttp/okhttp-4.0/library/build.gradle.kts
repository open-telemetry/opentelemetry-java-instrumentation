plugins {
  id("otel.library-instrumentation")
  id("otel.nullaway-conventions")
}

dependencies {
  library("com.squareup.okhttp3:okhttp:3.0.0")
}
