plugins {
  id("otel.java-conventions")
}

dependencies {
  compileOnly("org.elasticsearch.client:transport:5.3.0")
  compileOnly("org.elasticsearch:elasticsearch:5.3.0")

  implementation("io.opentelemetry.javaagent:opentelemetry-testing-common")
}
