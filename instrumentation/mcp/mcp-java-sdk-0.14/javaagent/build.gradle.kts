plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("io.modelcontextprotocol.sdk")
    module.set("mcp-core")
    versions.set("[0.14.1,)")
    assertInverse.set(true)
  }
}

// MCP Java SDK's minimum supported Java version is 17.
otelJava {
  minJavaVersionSupported.set(JavaVersion.VERSION_17)
}

dependencies {
  compileOnly(project(":instrumentation-annotations-support"))
  implementation(project(":instrumentation:reactor:reactor-3.1:library"))

  library("io.modelcontextprotocol.sdk:mcp-core:0.14.1")
  testLibrary("io.modelcontextprotocol.sdk:mcp-json-jackson2:0.14.1")
  testInstrumentation(project(":instrumentation:reactor:reactor-3.1:javaagent"))
  latestDepTestLibrary("io.modelcontextprotocol.sdk:mcp-core:latest.release")
  latestDepTestLibrary("io.modelcontextprotocol.sdk:mcp-json-jackson2:latest.release")
}

tasks {
  test {
    systemProperty("collectMetadata", otelProps.collectMetadata)
  }
}
