plugins {
  id("otel.java-conventions")
}

dependencies {
  api("io.opentelemetry.javaagent:opentelemetry-instrumentation-testing")

  compileOnly("com.alibaba:druid:1.0.0")
}
