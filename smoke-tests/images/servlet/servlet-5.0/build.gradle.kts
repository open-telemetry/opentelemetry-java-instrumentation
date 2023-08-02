plugins {
  war

  id("otel.java-conventions")
}

dependencies {
  compileOnly("jakarta.servlet:jakarta.servlet-api:6.0.0")
}
