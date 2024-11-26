plugins {
  id("otel.javaagent-testing")
}

dependencies {
  compileOnly("io.opentelemetry:opentelemetry-sdk-common")
}

otelJava {
  minJavaVersionSupported.set(JavaVersion.VERSION_18)
}
