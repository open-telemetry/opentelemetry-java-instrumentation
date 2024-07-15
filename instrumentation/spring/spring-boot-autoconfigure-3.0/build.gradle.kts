plugins {
  id("otel.library-instrumentation")
  id("otel.japicmp-conventions")
}

base.archivesName.set("opentelemetry-spring-boot-autoconfigure-3.0")
group = "io.opentelemetry.instrumentation"

otelJava {
  minJavaVersionSupported.set(JavaVersion.VERSION_17)
}

dependencies {
  val springBootVersion = "3.2.4"
  library("org.springframework.boot:spring-boot-starter-web:$springBootVersion")
  implementation(project(":instrumentation:spring:spring-boot-autoconfigure-2.7"))
  implementation("io.opentelemetry:opentelemetry-sdk-extension-autoconfigure")
  implementation(project(":instrumentation:spring:spring-web:spring-web-3.1:library"))
  implementation(project(":instrumentation:spring:spring-webmvc:spring-webmvc-6.0:library"))
  compileOnly("jakarta.servlet:jakarta.servlet-api:5.0.0")

  testLibrary("org.springframework.boot:spring-boot-starter-test:$springBootVersion") {
    exclude("org.junit.vintage", "junit-vintage-engine")
  }
}
