plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
}

dependencies {
  compileOnly("jakarta.servlet:jakarta.servlet-api:5.0.0")
  compileOnly("com.google.auto.value:auto-value-annotations")
  annotationProcessor("com.google.auto.value:auto-value")
  compileOnly("org.springframework:spring-web:6.0.0")
  compileOnly("com.fasterxml.jackson.core:jackson-databind")
}
