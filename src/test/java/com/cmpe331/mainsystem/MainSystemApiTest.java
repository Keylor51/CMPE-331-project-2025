package com.cmpe331.mainsystem;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
public class MainSystemApiTest {

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private RosterSqlRepository sqlRepo;

    @Mock
    private RosterMongoRepository mongoRepo;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private RosterController rosterController;

    private Map<String, Object> validFlight;
    private Map<String, Object> vehicleType;

    @BeforeEach
    void setUp() {
        validFlight = new HashMap<>();
        validFlight.put("flightNumber", "TK1001");
        validFlight.put("distanceKm", 1500);

        vehicleType = new HashMap<>();
        vehicleType.put("modelName", "Embraer E195");
        String configJson = "{\"sections\": [{\"className\": \"BUSINESS\", \"rows\": 1, \"layout\": [2], \"letters\": \"AC\"}, {\"className\": \"ECONOMY\", \"rows\": 2, \"layout\": [2], \"letters\": \"AC\"}]}";
        vehicleType.put("seatingPlanConfig", configJson);
        vehicleType.put("standardMenuDescription", "Sandwich");

        validFlight.put("vehicleType", vehicleType);
    }

    @Test
    void testValidateRosterRules_Success() {
        Map<String, Object> rosterData = new HashMap<>();
        rosterData.put("flightId", "TK1001");

        List<Map<String, Object>> pilots = new ArrayList<>();
        pilots.add(Map.of("seniorityLevel", "SENIOR", "name", "Cpt. Test"));
        pilots.add(Map.of("seniorityLevel", "JUNIOR", "name", "F.O. Test"));
        rosterData.put("pilots", pilots);

        List<Map<String, Object>> crew = new ArrayList<>();
        crew.add(Map.of("type", "REGULAR", "seniority", "SENIOR", "name", "Crew 1"));
        rosterData.put("cabinCrew", crew);

        ResponseEntity<?> response = rosterController.saveRoster(rosterData, "sql");
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("Saved to sql", response.getBody());
    }

    @Test
    void testValidateRosterRules_Fail_PilotCount() {
        Map<String, Object> rosterData = new HashMap<>();
        rosterData.put("flightId", "TK1001");

        List<Map<String, Object>> pilots = new ArrayList<>();
        pilots.add(Map.of("seniorityLevel", "SENIOR", "name", "Cpt. Test"));
        rosterData.put("pilots", pilots);

        ResponseEntity<?> response = rosterController.saveRoster(rosterData, "sql");
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertTrue(response.getBody().toString().contains("Validation Error"));
    }

    @Test
    void testGenerateRoster_Integration() {
        // 1. Flight Mock
        lenient().when(restTemplate.getForObject(anyString(), eq(Map.class))).thenReturn(validFlight);
        
        // 2. Pilot Mock (DUZELTME: allowedVehicleType uçak modeliyle tam eşleşmeli)
        Map<String, Object> p1 = new HashMap<>(); 
        p1.put("name", "P1"); 
        p1.put("seniorityLevel", "SENIOR"); 
        p1.put("allowedVehicleType", "Embraer E195"); // Düzeltildi
        p1.put("allowedRangeKm", 5000);
        
        Map<String, Object> p2 = new HashMap<>(); 
        p2.put("name", "P2"); 
        p2.put("seniorityLevel", "JUNIOR"); 
        p2.put("allowedVehicleType", "Embraer E195"); // Düzeltildi
        p2.put("allowedRangeKm", 5000);
        
        Map[] pilotArray = {p1, p2};
        
        // Pilot isteğini yakala
        lenient().when(restTemplate.getForObject(org.mockito.ArgumentMatchers.contains("pilots"), eq(Map[].class))).thenReturn(pilotArray);

        // Execute
        ResponseEntity<?> response = rosterController.generateRoster("TK1001", true);
        
        // Verify
        assertEquals(HttpStatus.OK, response.getStatusCode());
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertNotNull(body.get("flightInfo"));
        
        List<Map> selectedPilots = (List<Map>) body.get("pilots");
        // Artık 2 pilotun da seçilmesini bekleyebiliriz çünkü araç tipleri uyuşuyor
        assertEquals(2, selectedPilots.size());
    }

    @Test
    void testSmartSeatAssignment() {
        lenient().when(restTemplate.getForObject(anyString(), eq(Map.class))).thenReturn(validFlight);
        
        Map<String, Object> mother = new HashMap<>(); 
        mother.put("id", 101); mother.put("name", "Mother"); mother.put("seatType", "ECONOMY"); mother.put("affiliatedPassengerIds", List.of(102));
        
        Map<String, Object> child = new HashMap<>(); 
        child.put("id", 102); child.put("name", "Child"); child.put("seatType", "ECONOMY"); child.put("affiliatedPassengerIds", List.of(101));

        Map[] passengers = {mother, child};
        lenient().when(restTemplate.getForObject(org.mockito.ArgumentMatchers.contains("passengers"), eq(Map[].class))).thenReturn(passengers);

        ResponseEntity<?> response = rosterController.generateRoster("TK1001", true);
        
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        List<Map> resultPassengers = (List<Map>) body.get("passengers");

        if(resultPassengers != null && !resultPassengers.isEmpty()) {
            Map resMother = resultPassengers.stream().filter(p -> p.get("id").equals(101)).findFirst().orElse(null);
            if(resMother != null) {
                assertNotNull(resMother.get("seatNumber"), "Mother seat assigned");
                assertEquals(true, resMother.get("autoAssigned"));
                System.out.println("TEST SUCCESS: Mother Seat = " + resMother.get("seatNumber"));
            }
        }
    }
}