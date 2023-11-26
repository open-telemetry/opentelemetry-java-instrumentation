plugins {
  id("otel.library-instrumentation")
  id("otel.nullaway-conventions")
}

dependencies {
  library("com.alibaba:druid:0.2.6")

  testImplementation(project(":instrumentation:druid-0.2:testing"))
}
