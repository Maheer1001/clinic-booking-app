package model;

import java.time.LocalDateTime;

public class BookingReminder {

    private int brRemId;
    private int userId;
    private boolean notify;
    private int daysSinceLast;
    private String customMessage;
    private String notificationMethod;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public BookingReminder(int brRemId, int userId, boolean notify, int daysSinceLast,
                           String customMessage, String notificationMethod) {
        this.brRemId = brRemId;
        this.userId = userId;
        this.notify = notify;
        this.daysSinceLast = daysSinceLast;
        this.customMessage = customMessage;
        this.notificationMethod = notificationMethod;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public int getBrRemId() { return brRemId; }
    public int getUserId() { return userId; }
    public boolean isNotify() { return notify; }
    public void setNotify(boolean notify) { this.notify = notify; this.updatedAt = LocalDateTime.now(); }
    public int getDaysSinceLast() { return daysSinceLast; }
    public void setDaysSinceLast(int daysSinceLast) { this.daysSinceLast = daysSinceLast; this.updatedAt = LocalDateTime.now(); }
    public String getCustomMessage() { return customMessage; }
    public void setCustomMessage(String customMessage) { this.customMessage = customMessage; this.updatedAt = LocalDateTime.now(); }
    public String getNotificationMethod() { return notificationMethod; }
    public void setNotificationMethod(String notificationMethod) { this.notificationMethod = notificationMethod; this.updatedAt = LocalDateTime.now(); }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}