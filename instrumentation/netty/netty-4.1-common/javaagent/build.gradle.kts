plugins {
  id("otel.javaagent-instrumentation")
}

dependencies {
  api(project(":instrumentation:netty:netty-4-common:javaagent"))

  compileOnly("io.netty:netty-codec-http:4.1.0.Final")
}
