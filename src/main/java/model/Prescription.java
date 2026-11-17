package model;

import java.time.LocalDate;

public class Prescription {

    private int prescriptionId;
    private int userId;
    private String medicineName;
    private String dosage;
    private LocalDate startDate;
    private LocalDate endDate;
    private Integer dosesPerDay;

    public Prescription(int prescriptionId, int userId, String medicineName,
                        String dosage, LocalDate startDate, LocalDate endDate) {
        this(prescriptionId, userId, medicineName, dosage, startDate, endDate, null);
    }

    public Prescription(int prescriptionId, int userId, String medicineName,
                        String dosage, LocalDate startDate, LocalDate endDate, Integer dosesPerDay) {
        this.prescriptionId = prescriptionId;
        this.userId = userId;
        this.medicineName = medicineName;
        this.dosage = dosage;
        this.startDate = startDate;
        this.endDate = endDate;
        this.dosesPerDay = dosesPerDay;
    }

    public int getPrescriptionId() { return prescriptionId; }
    public int getUserId() { return userId; }
    public String getMedicineName() { return medicineName; }
    public String getDosage() { return dosage; }
    public LocalDate getStartDate() { return startDate; }
    public LocalDate getEndDate() { return endDate; }
    public Integer getDosesPerDay() { return dosesPerDay; }
}