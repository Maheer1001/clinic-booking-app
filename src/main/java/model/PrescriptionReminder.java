package model;

import java.time.LocalDateTime;

public class PrescriptionReminder {

    private int prRemId;
    private int userId;
    private int prescriptionId;
    private boolean notify;
    private int intervalBetweenDoses;
    private String customMessage;
    private String notificationMethod;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public PrescriptionReminder(int prRemId, int userId, int prescriptionId, boolean notify,
                                int intervalBetweenDoses, String customMessage, String notificationMethod) {
        this.prRemId = prRemId;
        this.userId = userId;
        this.prescriptionId = prescriptionId;
        this.notify = notify;
        this.intervalBetweenDoses = intervalBetweenDoses;
        this.customMessage = customMessage;
        this.notificationMethod = notificationMethod;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public int getPrRemId() { return prRemId; }
    public int getUserId() { return userId; }
    public int getPrescriptionId() { return prescriptionId; }
    public boolean isNotify() { return notify; }
    public void setNotify(boolean notify) { this.notify = notify; this.updatedAt = LocalDateTime.now(); }
    public int getIntervalBetweenDoses() { return intervalBetweenDoses; }
    public void setIntervalBetweenDoses(int intervalBetweenDoses) { this.intervalBetweenDoses = intervalBetweenDoses; this.updatedAt = LocalDateTime.now(); }
    public String getCustomMessage() { return customMessage; }
    public void setCustomMessage(String customMessage) { this.customMessage = customMessage; this.updatedAt = LocalDateTime.now(); }
    public String getNotificationMethod() { return notificationMethod; }
    public void setNotificationMethod(String notificationMethod) { this.notificationMethod = notificationMethod; this.updatedAt = LocalDateTime.now(); }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}