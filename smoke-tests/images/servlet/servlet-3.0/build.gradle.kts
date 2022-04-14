plugins {
  war

  id("otel.java-conventions")
}

dependencies {
  implementation("javax.servlet:javax.servlet-api:3.0.1")
}
