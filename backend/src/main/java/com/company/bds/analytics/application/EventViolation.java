package com.company.bds.analytics.application;

/** One reason an event batch was rejected: the JSON path of the value, a machine code and a Vietnamese message. */
public record EventViolation(String field, String code, String message) {}
