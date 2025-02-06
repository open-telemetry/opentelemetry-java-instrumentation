plugins {
  id("otel.java-conventions")
}

otelJava {
  minJavaVersionSupported.set(JavaVersion.VERSION_17)
}

dependencies {
  api(project(":testing-common"))
}
