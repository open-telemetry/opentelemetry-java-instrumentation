plugins {
  id("otel.java-conventions")
}

dependencies {
  api(project(":testing-common"))
  implementation("org.apache.rocketmq:rocketmq-test:4.8.0")

  implementation("com.google.guava:guava")
  implementation("org.apache.groovy:groovy")
  implementation("io.opentelemetry:opentelemetry-api")
  implementation("org.spockframework:spock-core")
}
