package com.example.universalconfig.core;

import java.util.ArrayList;
import java.util.List;

public final class ProfileDiff {
    public RiskLevel riskLevel = RiskLevel.LOW;
    public List<String> warnings = new ArrayList<>();
    public List<String> addedFiles = new ArrayList<>();
    public List<String> replacedFiles = new ArrayList<>();
    public List<String> changedKeybinds = new ArrayList<>();
    public List<String> skippedItems = new ArrayList<>();
    public List<String> checksumWarnings = new ArrayList<>();

    public void raiseRisk(RiskLevel riskLevel) {
        if (riskLevel.ordinal() > this.riskLevel.ordinal()) {
            this.riskLevel = riskLevel;
        }
    }
}
