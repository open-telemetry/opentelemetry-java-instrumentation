val stableVersion = "1.33.0"
val alphaVersion = "1.33.0-alpha"

allprojects {
  if (findProperty("otel.stable") != "true") {
    version = alphaVersion
  } else {
    version = stableVersion
  }
}
