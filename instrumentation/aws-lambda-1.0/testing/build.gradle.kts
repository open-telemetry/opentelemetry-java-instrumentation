plugins {
  id("otel.java-conventions")
}

dependencies {
  api(project(":testing-common"))

  api("com.amazonaws:aws-lambda-java-core:1.0.0")
  compileOnly("com.amazonaws:aws-lambda-java-events:2.2.1")

  implementation("com.google.guava:guava")

  implementation("org.codehaus.groovy:groovy-all")
  implementation("io.opentelemetry:opentelemetry-api")
  implementation("org.spockframework:spock-core")
  implementation("com.github.stefanbirkner:system-lambda")
}
