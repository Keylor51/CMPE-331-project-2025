package com.cmpe331.passenger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.CommandLineRunner;
import org.springframework.http.ResponseEntity;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PassengerApiTest {

    @Mock
    private PassengerRepository repo;

    @InjectMocks
    private PassengerController controller;
    
    @InjectMocks
    private PassengerApi api;

    @Test
    void testGetAll() {
        Passenger p = new Passenger("P1", 20, "M", "UK", "TK1", "Eco", "1A");
        when(repo.findAll()).thenReturn(Collections.singletonList(p));
        List<Passenger> res = controller.getAll();
        assertEquals(1, res.size());
    }

    @Test
    void testGetByFlight() {
        Passenger p = new Passenger("P1", 20, "M", "UK", "TK1", "Eco", "1A");
        when(repo.findByFlightId("TK1")).thenReturn(Collections.singletonList(p));
        
        ResponseEntity<List<Passenger>> res = controller.getByFlight("TK1");
        assertEquals(1, res.getBody().size());
    }

    @Test
    void testEntityFullCoverage() {
        Passenger p = new Passenger();
        p = new Passenger("Name", 10, "F", "TR", "F1", "BUS", "1A");
        
        p.setParentId(5L);
        p.setAffiliatedPassengerIds(Set.of(2L));
        p.setSeatNumber("2B");
        
        assertEquals("Name", p.getName());
        assertEquals(10, p.getAge());
        assertEquals("F", p.getGender());
        assertEquals("TR", p.getNationality());
        assertEquals("F1", p.getFlightId());
        assertEquals("BUS", p.getSeatType());
        assertEquals("2B", p.getSeatNumber());
        assertEquals(5L, p.getParentId());
        assertTrue(p.getAffiliatedPassengerIds().contains(2L));
        assertNull(p.getId());
    }

    @Test
    void testInitData() throws Exception {
        when(repo.count()).thenReturn(0L);
        
        AtomicLong idCounter = new AtomicLong(1);

        when(repo.save(any(Passenger.class))).thenAnswer(invocation -> {
            Passenger p = invocation.getArgument(0);
            Field idField = Passenger.class.getDeclaredField("id");
            idField.setAccessible(true);
            if (idField.get(p) == null) {
                idField.set(p, idCounter.getAndIncrement());
            }
            return p;
        });
        
        CommandLineRunner runner = api.initPassengers(repo);
        runner.run();
        verify(repo, atLeastOnce()).save(any(Passenger.class));
    }
    
    @Test
    void testSecurityConfigBeans() {
        SecurityConfig config = new SecurityConfig();
        assertNotNull(config.userDetailsService());
    }
}