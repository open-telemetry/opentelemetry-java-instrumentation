plugins {
  id("otel.java-conventions")
}

val jsonrpcVersion = "1.3.3"

dependencies {
  api(project(":testing-common"))

  implementation("com.github.briandilley.jsonrpc4j:jsonrpc4j:$jsonrpcVersion")

  implementation("com.fasterxml.jackson.core:jackson-databind:2.13.3")
}
