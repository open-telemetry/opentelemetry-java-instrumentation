plugins {
  id("otel.java-conventions")
}

dependencies {
  testImplementation(project(":instrumentation:reactor:reactor-netty:reactor-netty-1.0:javaagent"))
  testImplementation("io.projectreactor.netty:reactor-netty-http:1.0.0")
}
