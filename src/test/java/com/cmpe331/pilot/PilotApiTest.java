package com.cmpe331.pilot;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.CommandLineRunner;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PilotApiTest {

    @Mock
    private PilotRepository pilotRepository;

    @InjectMocks
    private PilotController pilotController;

    @InjectMocks
    private PilotApi pilotApi;

    @Test
    void testGetAllPilots() {
        Pilot p = new Pilot("Capt. Test", 50, "Male", "USA", 10000, "Boeing", "SENIOR");
        when(pilotRepository.findAll()).thenReturn(Collections.singletonList(p));
        List<Pilot> result = pilotController.getAll();
        assertEquals(1, result.size());
        assertEquals("Capt. Test", result.get(0).getName());
    }

    @Test
    void testPilotEntityCoverage() {
        Pilot p = new Pilot();
        p = new Pilot("Name", 30, "Female", "TR", 5000, "737", "JUNIOR");
        
        Set<String> langs = new HashSet<>();
        langs.add("English");
        p.setLanguages(langs);
        
        assertEquals("Name", p.getName());
        assertEquals(30, p.getAge());
        assertEquals("Female", p.getGender());
        assertEquals("TR", p.getNationality());
        assertEquals(5000, p.getAllowedRangeKm());
        assertEquals("737", p.getAllowedVehicleType());
        assertEquals("JUNIOR", p.getSeniorityLevel());
        assertEquals(langs, p.getLanguages());
        assertNull(p.getId());
    }

    @Test
    void testInitData() throws Exception {
        when(pilotRepository.count()).thenReturn(0L);
        CommandLineRunner runner = pilotApi.initPilots(pilotRepository);
        runner.run();
        verify(pilotRepository, atLeastOnce()).save(any(Pilot.class));
    }
    
    @Test
    void testInitDataSkipped() throws Exception {
        when(pilotRepository.count()).thenReturn(10L);
        CommandLineRunner runner = pilotApi.initPilots(pilotRepository);
        runner.run();
        verify(pilotRepository, never()).save(any(Pilot.class));
    }
    
    @Test
    void testSecurityConfigBeans() {
        SecurityConfig config = new SecurityConfig();
        assertNotNull(config.userDetailsService());
    }
}