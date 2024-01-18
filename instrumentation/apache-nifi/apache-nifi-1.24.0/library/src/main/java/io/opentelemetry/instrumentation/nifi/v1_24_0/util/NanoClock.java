//package io.opentelemetry.instrumentation.nifi.v1_24_0.util;
//
//import java.time.Clock;
//import java.time.Instant;
//import java.time.ZoneId;
//import java.time.ZoneOffset;
//import java.util.Objects;
//
//public final class NanoClock extends Clock {
//
//  private static final long EPOCH_NANOS = System.currentTimeMillis() * 1_000_000;
//  private static final long NANO_START = System.nanoTime();
//  private static final long OFFSET_NANOS = EPOCH_NANOS - NANO_START;
//
//  private static final NanoClock UTC_INSTANCE = new NanoClock(ZoneOffset.UTC);
//  private static final NanoClock DEFAULT_INSTANCE = new NanoClock(ZoneId.systemDefault());
//
//  public static final long NANOS_PER_SECOND = 1_000_000_000;
//
//  private final ZoneId zone;
//
//  private NanoClock(ZoneId zone)  {
//    this.zone = Objects.requireNonNull(zone, "zone");
//  }
//
//  @Override
//  public ZoneId getZone() {
//    return this.zone;
//  }
//
//  @Override
//  public NanoClock withZone(ZoneId zone) {
//    return zone.equals(this.zone) ? this : new NanoClock(zone);
//  }
//
//  @Override
//  public long millis() {
//    return System.currentTimeMillis();
//  }
//
//  public long nanos() {
//    return System.nanoTime() + OFFSET_NANOS;
//  }
//
//  @Override
//  public Instant instant() {
//    long now = nanos();
//    return Instant.ofEpochSecond(now/NANOS_PER_SECOND, now%NANOS_PER_SECOND);
//  }
//
//  @Override
//  public String toString() {
//    return "NanoClock[" + this.zone + "]";
//  }
//
//  public static NanoClock system(ZoneId zone) {
//    return new NanoClock(zone);
//  }
//
//  public static NanoClock systemUtc() {
//    return UTC_INSTANCE;
//  }
//
//  public static NanoClock systemDefaultZone() {
//    return DEFAULT_INSTANCE;
//  }
//
//  public static long tick() {
//    return NanoClock.systemDefaultZone().nanos();
//  }
//}
