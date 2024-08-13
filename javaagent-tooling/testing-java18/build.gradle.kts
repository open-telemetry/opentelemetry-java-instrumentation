plugins {
  id("otel.javaagent-instrumentation")
}

dependencies {
  // To be able to have it as part of agent,
  // this dependency needs to be added as "testInstrumentation", not as "testImplementation"
  testInstrumentation(project(":instrumentation:resources:library"))
}

otelJava {
  minJavaVersionSupported.set(JavaVersion.VERSION_18)
}
