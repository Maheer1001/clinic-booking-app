package model;

import java.time.LocalDateTime;

public class Appointment {

    private int appId;
    private int userId;
    private String serviceName;
    private String clinicName;
    private String doctorName;
    private LocalDateTime appointmentDateTime;

    public Appointment(int appId, int userId, String serviceName, String clinicName,
                       String doctorName, LocalDateTime appointmentDateTime) {
        this.appId = appId;
        this.userId = userId;
        this.serviceName = serviceName;
        this.clinicName = clinicName;
        this.doctorName = doctorName;
        this.appointmentDateTime = appointmentDateTime;
    }

    public int getAppId() { return appId; }
    public int getUserId() { return userId; }
    public String getServiceName() { return serviceName; }
    public String getClinicName() { return clinicName; }
    public String getDoctorName() { return doctorName; }
    public LocalDateTime getAppointmentDateTime() { return appointmentDateTime; }
}