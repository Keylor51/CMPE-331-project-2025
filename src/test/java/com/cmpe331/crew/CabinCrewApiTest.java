package com.cmpe331.crew;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.CommandLineRunner;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CabinCrewApiTest {

    @Mock
    private CabinCrewRepository repository;

    @InjectMocks
    private CabinCrewController controller;
    
    @InjectMocks
    private CabinCrewApi api;

    @Test
    void testGetAllCrew() {
        CabinCrewMember c = new CabinCrewMember("Crew Test", 25, "Male", "TR", "REGULAR", "JUNIOR");
        when(repository.findAll()).thenReturn(Collections.singletonList(c));

        List<CabinCrewMember> result = controller.getAll();
        assertEquals(1, result.size());
        assertEquals("Crew Test", result.get(0).getName());
    }

    @Test
    void testCrewEntityFullCoverage() {
        CabinCrewMember c = new CabinCrewMember(); 
        c = new CabinCrewMember("Name", 22, "F", "DE", "CHIEF", "SENIOR");

        c.setLanguages(Set.of("EN"));
        c.setAllowedVehicles(Set.of("V1"));
        c.setChefRecipes(Set.of("R1"));

        assertEquals("Name", c.getName());
        assertEquals(22, c.getAge());
        assertEquals("F", c.getGender());
        assertEquals("DE", c.getNationality());
        assertEquals("CHIEF", c.getType());
        assertEquals("SENIOR", c.getSeniority());
        assertTrue(c.getLanguages().contains("EN"));
        assertTrue(c.getAllowedVehicles().contains("V1"));
        assertTrue(c.getChefRecipes().contains("R1"));
        assertNull(c.getId());
    }

    @Test
    void testInitData() throws Exception {
        when(repository.count()).thenReturn(0L);
        CommandLineRunner runner = api.initCrew(repository);
        runner.run();
        verify(repository, atLeastOnce()).save(any(CabinCrewMember.class));
    }
    
    @Test
    void testSecurityConfigBeans() {
        SecurityConfig config = new SecurityConfig();
        assertNotNull(config.userDetailsService());
    }
}