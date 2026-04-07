package com.example.lotterydrawdemo.lottery;

public final class PrizeTierDefinition {

    private final String prizeCode;
    private final String prizeName;
    private final int prizeCount;

    public PrizeTierDefinition(String prizeCode, String prizeName, int prizeCount) {
        if (prizeCode == null || prizeCode.trim().isEmpty()) {
            throw new IllegalArgumentException("prizeCode must not be blank");
        }
        if (prizeName == null || prizeName.trim().isEmpty()) {
            throw new IllegalArgumentException("prizeName must not be blank");
        }
        if (prizeCount <= 0) {
            throw new IllegalArgumentException("prizeCount must be positive");
        }
        this.prizeCode = prizeCode;
        this.prizeName = prizeName;
        this.prizeCount = prizeCount;
    }

    public String prizeCode() {
        return prizeCode;
    }

    public String prizeName() {
        return prizeName;
    }

    public int prizeCount() {
        return prizeCount;
    }
}
