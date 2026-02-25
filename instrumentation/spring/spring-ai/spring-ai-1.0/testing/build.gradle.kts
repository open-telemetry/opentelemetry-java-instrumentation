plugins {
  id("otel.java-conventions")
}

otelJava {
  minJavaVersionSupported.set(JavaVersion.VERSION_17)
}

dependencies {
  implementation(project(":testing-common"))

  api("org.springframework.ai:spring-ai-openai:1.0.0")
  api("org.springframework.ai:spring-ai-client-chat:1.0.0")
  api(project(":instrumentation-api-incubator"))
}
