plugins {
  id("otel.library-instrumentation")
}

val jsonrpcVersion = "1.6"
val jacksonVersion = "2.13.3"

dependencies {
  library("com.github.briandilley.jsonrpc4j:jsonrpc4j:$jsonrpcVersion")

  implementation("com.fasterxml.jackson.core:jackson-databind:$jacksonVersion")

  testImplementation(project(":instrumentation:jsonrpc4j-1.6:testing"))
}
