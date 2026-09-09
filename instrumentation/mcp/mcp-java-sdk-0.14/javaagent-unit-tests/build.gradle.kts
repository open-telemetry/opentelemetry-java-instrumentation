plugins {
  id("otel.java-conventions")
}

otelJava {
  minJavaVersionSupported.set(JavaVersion.VERSION_17)
}

dependencies {
  testImplementation(project(":instrumentation-api"))
  testImplementation(project(":instrumentation:mcp:mcp-java-sdk-0.14:javaagent"))
  testImplementation("io.modelcontextprotocol.sdk:mcp-core:0.14.1")
}
