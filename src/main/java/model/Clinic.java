package model;

import java.util.List;

public class Clinic {

    private int id;
    private String name;
    private String address;
    private String postcode;
    private String phone;
    private List<Service> services;
    private Coordinate coords;
    private List<String> disabilities;

    public Clinic(int id, String name, String address,
                  String postcode, String phone,
                  List<Service> services, Coordinate coords, List<String> disabilities) {

        this.id = id;
        this.name = name;
        this.address = address;
        this.postcode = postcode;
        this.phone = phone;
        this.services = services;
        this.coords = coords;
        this.disabilities = disabilities;
    }

    public Clinic(String name, String address,
                  String postcode, String phone,
                  List<Service> services, Coordinate coords, List<String> disabilities) {
        this(0, name, address, postcode, phone, services, coords, disabilities);
    }


    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public String getName() { return name; }
    public String getAddress() { return address; }
    public String getPostcode() { return postcode; }
    public String getPhone() { return phone; }
    public List<String> getDissAcc() {return disabilities; }

    public List<Service> getServices() { return services; }
    public void setServices(List<Service> services) {
        this.services = services;
    }

    public Coordinate getCoords() { return coords; }

    @Override
    public String toString() {
        return name + " (" + postcode + ")";
    }
}
