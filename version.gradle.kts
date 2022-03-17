allprojects {
  if (findProperty("otel.stable") != "true") {
    version = "1.12.1-alpha"
  } else {
    version = "1.12.1"
  }
}
