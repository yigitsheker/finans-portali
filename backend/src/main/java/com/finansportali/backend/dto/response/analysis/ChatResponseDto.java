package com.finansportali.backend.dto.response.analysis;

import java.time.Instant;
import java.util.List;

/**
 * Structured AI answer. Markdown-friendly {@code reply} body plus optional
 * {@code scenarios} array used when the question implies a budget / risk
 * spread (e.g. "5000 TL'm var, ne yapayım?" — answer breaks into safe,
 * balanced and aggressive scenarios so the UI can render them as cards).
 * {@code disclaimer} is always populated — frontends render it verbatim.
 */
public class ChatResponseDto {
    private String reply;
    private List<Scenario> scenarios;
    private String disclaimer;
    private Instant timestamp;

    public ChatResponseDto() {}

    public String getReply() { return reply; }
    public void setReply(String reply) { this.reply = reply; }
    public List<Scenario> getScenarios() { return scenarios; }
    public void setScenarios(List<Scenario> scenarios) { this.scenarios = scenarios; }
    public String getDisclaimer() { return disclaimer; }
    public void setDisclaimer(String disclaimer) { this.disclaimer = disclaimer; }
    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }

    public static class Scenario {
        private String label;       // "Güvenli" | "Dengeli" | "Riskli"
        private String description;
        private List<Allocation> allocations;

        public Scenario() {}
        public Scenario(String label, String description, List<Allocation> allocations) {
            this.label = label;
            this.description = description;
            this.allocations = allocations;
        }
        public String getLabel() { return label; }
        public void setLabel(String label) { this.label = label; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public List<Allocation> getAllocations() { return allocations; }
        public void setAllocations(List<Allocation> allocations) { this.allocations = allocations; }
    }

    public static class Allocation {
        private String assetClass;
        private int percent;

        public Allocation() {}
        public Allocation(String assetClass, int percent) {
            this.assetClass = assetClass;
            this.percent = percent;
        }
        public String getAssetClass() { return assetClass; }
        public void setAssetClass(String assetClass) { this.assetClass = assetClass; }
        public int getPercent() { return percent; }
        public void setPercent(int percent) { this.percent = percent; }
    }
}
