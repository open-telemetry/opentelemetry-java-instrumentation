allprojects {
  if (findProperty("otel.stable") != "true") {
    version = "1.9.2-alpha"
  } else {
    version = "1.9.2"
  }
}
