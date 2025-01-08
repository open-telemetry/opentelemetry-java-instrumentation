plugins {
  id("otel.library-instrumentation")
}

val jacksonVersion = "2.13.3"

dependencies {
  library("com.github.briandilley.jsonrpc4j:jsonrpc4j:1.6")

  library("com.fasterxml.jackson.core:jackson-databind:$jacksonVersion")

  testImplementation(project(":instrumentation:jsonrpc4j-1.6:testing"))
}
