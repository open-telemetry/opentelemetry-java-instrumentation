plugins {
  id("otel.library-instrumentation")
}

dependencies {
  compileOnly("org.springframework:spring-webmvc:6.0.0")
  compileOnly("jakarta.servlet:jakarta.servlet-api:5.0.0")

  testImplementation(project(":testing-common"))
  testImplementation("org.springframework.boot:spring-boot-starter-web:3.0.0")
  testImplementation("org.springframework.boot:spring-boot-starter-test:3.0.0")
}

// spring 6 requires java 17
otelJava {
  minJavaVersionSupported.set(JavaVersion.VERSION_17)
}