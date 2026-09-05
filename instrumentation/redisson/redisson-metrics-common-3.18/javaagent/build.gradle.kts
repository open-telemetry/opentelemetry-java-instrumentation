plugins {
  id("otel.javaagent-instrumentation")
}

dependencies {
  compileOnly("org.redisson:redisson:3.18.0")
}
