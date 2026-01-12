plugins {
  id("otel.java-conventions")
}

otelJava {
  minJavaVersionSupported.set(JavaVersion.VERSION_11)
}

dependencies {
  api("io.opentelemetry.javaagent:opentelemetry-testing-common")
}
