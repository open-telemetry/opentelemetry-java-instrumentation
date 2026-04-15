plugins {
  id("otel.java-conventions")

  war
}

description = "JMX metrics - test web application"

dependencies {
  // use both servlet APIs to provide compatibility with most containers before/after Jakarta renaming
  compileOnly("jakarta.servlet:jakarta.servlet-api:5.0.0")
  compileOnly("javax.servlet:javax.servlet-api:4.0.1")
}
