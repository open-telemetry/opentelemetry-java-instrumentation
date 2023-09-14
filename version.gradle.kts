val stableVersion = "1.30.0"
val alphaVersion = "1.30.0-alpha"

allprojects {
  if (findProperty("otel.stable") != "true") {
    version = alphaVersion
  } else {
    version = stableVersion
  }
}
