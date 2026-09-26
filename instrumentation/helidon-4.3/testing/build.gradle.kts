plugins {
  id("otel.java-conventions")
}

otelJava {
  minJavaVersionSupported.set(JavaVersion.VERSION_21)
  if (
    otelProps.testLatestDeps ||
    otelProps.testJavaVersion?.isCompatibleWith(JavaVersion.VERSION_27) == true
  ) {
    maxJavaVersionSupported.set(JavaVersion.VERSION_27)
  }
}

dependencies {
  api("io.opentelemetry.javaagent:opentelemetry-testing-common")
  implementation("io.helidon.webserver:helidon-webserver:4.3.0")
}
