package io.opentelemetry.instrumentation.ktor.v1_0

import java.util.regex.Pattern

// Source: Regular Expressions Cookbook 2nd edition - 8.17.
// Matching IPv6 Addresses
private val ipv6 = Pattern.compile( // Non Compressed
  "^(?:(?:(?:[A-F0-9]{1,4}:){6}" // Compressed with at most 6 colons
    + "|(?=(?:[A-F0-9]{0,4}:){0,6}" // and 4 bytes and anchored
    + "(?:[0-9]{1,3}\\.){3}[0-9]{1,3}(?![:.\\w]))" // and at most 1 double colon
    + "(([0-9A-F]{1,4}:){0,5}|:)((:[0-9A-F]{1,4}){1,5}:|:)" // Compressed with 7 colons and 5 numbers
    + "|::(?:[A-F0-9]{1,4}:){5})" // 255.255.255.
    + "(?:(?:25[0-5]|2[0-4][0-9]|1[0-9][0-9]|[1-9]?[0-9])\\.){3}" // 255
    + "(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)" // Standard
    + "|(?:[A-F0-9]{1,4}:){7}[A-F0-9]{1,4}" // Compressed with at most 7 colons and anchored
    + "|(?=(?:[A-F0-9]{0,4}:){0,7}[A-F0-9]{0,4}(?![:.\\w]))" // and at most 1 double colon
    + "(([0-9A-F]{1,4}:){1,7}|:)((:[0-9A-F]{1,4}){1,7}|:)" // Compressed with 8 colons
    + "|(?:[A-F0-9]{1,4}:){7}:|:(:[A-F0-9]{1,4}){7})(?![:.\\w])\$",
  Pattern.CASE_INSENSITIVE)

// Source: Regular Expressions Cookbook 2nd edition - 8.16.
// Matching IPv4 Addresses
private val ipv4 = Pattern.compile("^(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}" +
  "(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\$")

fun isIpAddress(address : String) : Boolean {
  return ipv4.matcher(address).matches() || ipv6.matcher(address).matches()
}
