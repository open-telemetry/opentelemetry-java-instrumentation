plugins {
  id("otel.java-conventions")
}

otelJava {
  minJavaVersionSupported.set(JavaVersion.VERSION_17)
}

val versions: Map<String, String> by project
val springBootVersion = versions["org.springframework.boot"]

dependencies {
  // spring depdenencies are compile only to enable testing against different versions of spring
  compileOnly("org.springframework.boot:spring-boot-starter-web:$springBootVersion")
  compileOnly("org.springframework.boot:spring-boot-starter-webflux:$springBootVersion")
  compileOnly("org.springframework.boot:spring-boot-starter-test:$springBootVersion")
  api(project(":smoke-tests-otel-starter:spring-smoke-testing"))
}
