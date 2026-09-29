plugins {
  id("otel.library-instrumentation")
}

otelJava {
  minJavaVersionSupported.set(JavaVersion.VERSION_21)
  if (otelProps.testLatestDeps) {
    javaToolchainVersion.set(JavaVersion.VERSION_27)
    java.toolchain.vendor.set(JvmVendorSpec.AZUL)
  }
}

dependencies {
  library("io.helidon.webserver:helidon-webserver:4.3.0")
  testImplementation(project(":instrumentation:helidon-4.3:testing"))
}
