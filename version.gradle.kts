val stableVersion = "1.20.0"
val alphaVersion = "1.20.0-alpha"

allprojects {
  if (findProperty("otel.stable") != "true") {
    version = alphaVersion
  } else {
    version = stableVersion
  }
}
