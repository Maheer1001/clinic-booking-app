package model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ServiceTest {

    @Test
    void testConstructorAndGetters() {
        Service service = new Service(1, "Checkup", "General health", 30);

        assertEquals(1, service.getId());
        assertEquals("Checkup", service.getName());
        assertEquals("General health", service.getDescription());
        assertEquals(30, service.getDurationMinutes());
    }

    @Test
    void testSetId() {
        Service service = new Service(0, "A", "B", 10);
        service.setId(99);
        assertEquals(99, service.getId());
    }
}
