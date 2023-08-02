plugins {
  war

  id("otel.java-conventions")
}

dependencies {
  implementation("javax.servlet:javax.servlet-api:4.0.1")
}
