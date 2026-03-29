package com.example.redislockdemo.support;

public final class LockKeyBuilder {

    private LockKeyBuilder() {
    }

    public static String orderKey(String orderNo) {
        return "lock:order:" + orderNo;
    }

    public static String jobKey(String jobName) {
        return "lock:job:" + jobName;
    }

    public static String apiKey(String name) {
        return "lock:api:" + name;
    }
}
