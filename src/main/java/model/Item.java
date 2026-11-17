package model;

import java.time.LocalDate;
import java.time.LocalTime;

public class Item {

    private Service service;
    private LocalDate date;
    private LocalTime time;
    private int timeslotId;

    public Item(Service service) {
        this.service = service;
    }

    public Service getService() {
        return service;
    }

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    public LocalTime getTime() {
        return time;
    }

    public void setTime(LocalTime time) {
        this.time = time;
    }

    public void setTimeslotId(int id) { this.timeslotId = id; }
    public int getTimeslotId() { return timeslotId; }


}

