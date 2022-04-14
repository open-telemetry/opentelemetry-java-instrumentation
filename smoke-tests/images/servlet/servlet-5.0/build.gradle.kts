plugins {
  war

  id("otel.java-conventions")
}

dependencies {
  compileOnly("jakarta.servlet:jakarta.servlet-api:5.0.0")
}
