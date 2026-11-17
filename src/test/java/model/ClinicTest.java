package model;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;

class ClinicTest {

    @Test
    void testConstructorAndGetters() {
        Clinic clinic = new Clinic("Test Clinic", "123 St", "AB12CD", "0123456789", new ArrayList<>(), null);

        assertEquals("Test Clinic", clinic.getName());
        assertEquals("123 St", clinic.getAddress());
        assertEquals("AB12CD", clinic.getPostcode());
        assertEquals("0123456789", clinic.getPhone());
        assertTrue(clinic.getServices().isEmpty());
        assertNull(clinic.getCoords());
    }

    @Test
    void testIdConstructor() {
        Clinic clinic = new Clinic(5, "Named", "Addr", "PC", "Ph", new ArrayList<>(), new Coordinate(0, 0));
        assertEquals(5, clinic.getId());
    }

    @Test
    void testSetId() {
        Clinic clinic = new Clinic("A", "B", "C", "D", new ArrayList<>(), new Coordinate(0,0));
        clinic.setId(42);
        assertEquals(42, clinic.getId());
    }

    @Test
    void testSetServices() {
        Clinic clinic = new Clinic("A", "B", "C", "D", new ArrayList<>(), new Coordinate(0,0));
        Service s = new Service(1, "S", "desc", 30);
        clinic.setServices(new ArrayList<>() {{ add(s); }});
        assertEquals(1, clinic.getServices().size());
    }
}
