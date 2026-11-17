package model;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public class Book {

    private String firstName = "";
    private String lastName = "";
    private LocalDate dob;
    private String gender = "";
    private String sexuality = "";
    private String phone = "";
    private String email = "";

    private Clinic clinic;
    private Service service;
    private List<Item> items;

    private int timeslotId = -1;
    private LocalDate date;
    private LocalTime time;
    private String reason = "";
    private String disabilities = "";

    public String getFirstName() { return firstName; }
    public void setFirstName(String v) { firstName = v; }

    public String getLastName() { return lastName; }
    public void setLastName(String v) { lastName = v; }

    public LocalDate getDob() { return dob; }
    public void setDob(LocalDate v) { dob = v; }

    public String getGender() { return gender; }
    public void setGender(String v) { gender = v; }

    public String getSexuality() { return sexuality; }
    public void setSexuality(String v) { sexuality = v; }

    public String getPhone() { return phone; }
    public void setPhone(String v) { phone = v; }

    public String getEmail() { return email; }
    public void setEmail(String v) { email = v; }

    public Clinic getClinic() {
        return clinic;
    }

    public void setClinic(Clinic clinic) {
        this.clinic = clinic;
    }

    public Service getService() {
        return service;
    }

    public void setService(Service service) {
        this.service = service;
    }

    public List<Item> getItems() {
        return items;
    }

    public void setItems(List<Item> items) {
        this.items = items;
    }

    public int getTimeslotId() { return timeslotId; }
    public void setTimeslotId(int v) { timeslotId = v; }

    public LocalDate getDate() { return date; }
    public void setDate(LocalDate v) { date = v; }

    public LocalTime getTime() { return time; }
    public void setTime(LocalTime v) { time = v; }

    public String getReason() { return reason; }
    public void setReason(String v) { reason = v; }

    public String getDisabilities() { return disabilities; }
    public void setDisabilities(String v) { disabilities = v; }
}
