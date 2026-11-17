package model;

import java.time.LocalDateTime;

public class AppointmentReminder {

    private int arRemId;
    private int userId;
    private int appId;
    private boolean notify;
    // Stored as TEXT in DB; can be a number or future JSON.
    private String customDays;
    private String customMessage;
    private String notificationMethod;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public AppointmentReminder(int arRemId, int userId, int appId, boolean notify,
                               String customDays, String customMessage, String notificationMethod) {
        this.arRemId = arRemId;
        this.userId = userId;
        this.appId = appId;
        this.notify = notify;
        this.customDays = customDays;
        this.customMessage = customMessage;
        this.notificationMethod = notificationMethod;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public int getArRemId() { return arRemId; }
    public int getUserId() { return userId; }
    public int getAppId() { return appId; }
    public boolean isNotify() { return notify; }
    public void setNotify(boolean notify) { this.notify = notify; this.updatedAt = LocalDateTime.now(); }
    public String getCustomDays() { return customDays; }
    public void setCustomDays(String customDays) { this.customDays = customDays; this.updatedAt = LocalDateTime.now(); }
    public String getCustomMessage() { return customMessage; }
    public void setCustomMessage(String customMessage) { this.customMessage = customMessage; this.updatedAt = LocalDateTime.now(); }
    public String getNotificationMethod() { return notificationMethod; }
    public void setNotificationMethod(String notificationMethod) { this.notificationMethod = notificationMethod; this.updatedAt = LocalDateTime.now(); }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}