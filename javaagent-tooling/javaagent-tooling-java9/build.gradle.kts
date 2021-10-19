plugins {
  id("otel.java-conventions")
  id("otel.publish-conventions")
}

group = "io.opentelemetry.javaagent"

dependencies {
  implementation(project(":javaagent-bootstrap"))

  implementation("net.bytebuddy:byte-buddy-dep")
  implementation("org.slf4j:slf4j-api")

  testImplementation("net.bytebuddy:byte-buddy-agent")
}

otelJava {
  minJavaVersionSupported.set(JavaVersion.VERSION_1_9)
}
