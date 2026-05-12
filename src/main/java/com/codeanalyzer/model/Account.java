package com.codeanalyzer.model;

import java.time.LocalDateTime;

/**
 * Model đại diện cho tài khoản lập trình viên trên Codeforces.
 */
public class Account {
    private int id;
    private String username;
    private String platform; // CODEFORCES
    private String displayName;
    private LocalDateTime addedAt;
    private LocalDateTime lastCrawledAt;
    private boolean active;

    public Account() {}

    public Account(String username, String platform) {
        this.username = username;
        this.platform = platform;
        this.active = true;
    }

    // Getters & Setters
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPlatform() { return platform; }
    public void setPlatform(String platform) { this.platform = platform; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public LocalDateTime getAddedAt() { return addedAt; }
    public void setAddedAt(LocalDateTime addedAt) { this.addedAt = addedAt; }

    public LocalDateTime getLastCrawledAt() { return lastCrawledAt; }
    public void setLastCrawledAt(LocalDateTime lastCrawledAt) { this.lastCrawledAt = lastCrawledAt; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    @Override
    public String toString() {
        return username + " (" + platform + ")";
    }
}
