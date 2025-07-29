plugins {
  id("otel.java-conventions")
}

dependencies {
  api(project(":testing-common"))

  implementation("io.opentelemetry:opentelemetry-api")
  implementation("org.slf4j:slf4j-api")
  implementation("ch.qos.logback:logback-classic")
  implementation("org.slf4j:log4j-over-slf4j")
  implementation("org.slf4j:jcl-over-slf4j")
  implementation("org.slf4j:jul-to-slf4j")
}
