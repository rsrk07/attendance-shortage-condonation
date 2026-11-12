package com.attendance.condonation.service;

import java.math.BigDecimal;
import java.util.Map;

/** Small helpers for reading process variables parsed from JSON (Map / Number / String / Boolean). */
public final class Vars {

  private Vars() {}

  @SuppressWarnings("unchecked")
  public static Map<String, Object> map(Object value) {
    return value instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
  }

  /** Reads a nested value, e.g. {@code path(vars, "eligibility", "fee")}. */
  public static Object path(Map<String, Object> vars, String... keys) {
    Object current = vars;
    for (String key : keys) {
      if (!(current instanceof Map<?, ?> m)) {
        return null;
      }
      current = m.get(key);
    }
    return current;
  }

  public static String string(Object value) {
    return value == null ? null : value.toString();
  }

  public static BigDecimal decimal(Object value) {
    if (value instanceof BigDecimal b) {
      return b;
    }
    if (value instanceof Number n) {
      return new BigDecimal(n.toString());
    }
    if (value instanceof String s && !s.isBlank()) {
      try {
        return new BigDecimal(s.trim());
      } catch (NumberFormatException e) {
        return null;
      }
    }
    return null;
  }

  public static Integer integer(Object value) {
    BigDecimal d = decimal(value);
    return d == null ? null : d.intValue();
  }

  public static Boolean bool(Object value) {
    if (value instanceof Boolean b) {
      return b;
    }
    if (value instanceof String s) {
      return Boolean.parseBoolean(s);
    }
    return null;
  }
}
