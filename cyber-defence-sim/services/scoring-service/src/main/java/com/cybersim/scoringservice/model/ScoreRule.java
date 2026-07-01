package com.cybersim.scoringservice.model;

public enum ScoreRule {
    RED_LOW_FINDING("RED", 10, "Valid low-severity finding", false),
    RED_MEDIUM_FINDING("RED", 25, "Valid medium-severity finding", false),
    RED_HIGH_FINDING("RED", 50, "Valid high-severity finding", false),
    RED_CRITICAL_FINDING("RED", 100, "Valid critical-severity finding", false),
    RED_DUPLICATE_FINDING("RED", -5, "Duplicate finding penalty", false),
    RED_UNSAFE_ACTION("RED", -100, "Unsafe action penalty and agent block", true),
    BLUE_VALID_DETECTION("BLUE", 20, "Valid detection", false),
    BLUE_CORRECT_TRIAGE("BLUE", 20, "Correct finding triage", false),
    BLUE_VALID_REMEDIATION_PROPOSAL("BLUE", 25, "Valid remediation proposal", false),
    BLUE_PATCH_APPLIED("BLUE", 40, "Approved patch applied", false),
    BLUE_FIX_VERIFIED("BLUE", 30, "Fix verified", false),
    BLUE_FALSE_POSITIVE("BLUE", -10, "False-positive penalty", false),
    BLUE_FAILED_PATCH("BLUE", -15, "Failed patch penalty", false);

    private final String team;
    private final int points;
    private final String reason;
    private final boolean blocksAgent;

    ScoreRule(String team, int points, String reason, boolean blocksAgent) {
        this.team = team;
        this.points = points;
        this.reason = reason;
        this.blocksAgent = blocksAgent;
    }

    public String team() {
        return team;
    }

    public int points() {
        return points;
    }

    public String reason() {
        return reason;
    }

    public boolean blocksAgent() {
        return blocksAgent;
    }
}
