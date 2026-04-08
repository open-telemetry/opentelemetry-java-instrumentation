plugins {
  id("otel.java-conventions")
}

dependencies {
  api("io.opentelemetry.javaagent:opentelemetry-testing-common")

  compileOnly("com.alibaba:druid:1.0.0")
}
