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
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.client.RestTemplate;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

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
    void testGenerateRoster_Success_FromExternalApi() {
        when(sqlRepo.findByFlightIdOrderByGeneratedDateDesc(anyString())).thenReturn(Collections.emptyList());
        when(mongoRepo.findByFlightIdOrderByGeneratedDateDesc(anyString())).thenReturn(Collections.emptyList());

        lenient().when(restTemplate.getForObject(contains("flights/"), eq(Map.class))).thenReturn(validFlight);
        lenient().when(restTemplate.getForObject(contains("pilots"), eq(Map[].class))).thenReturn(new Map[]{});
        lenient().when(restTemplate.getForObject(contains("cabin-crew"), eq(Map[].class))).thenReturn(new Map[]{});
        lenient().when(restTemplate.getForObject(contains("passengers"), eq(Map[].class))).thenReturn(new Map[]{});

        ResponseEntity<?> response = rosterController.generateRoster("TK1001", false);
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void testGenerateRoster_FoundInSql_Directly() {
        RosterSqlEntity entity = new RosterSqlEntity();
        entity.setRosterData("{\"flightId\": \"TK1001\", \"source\": \"SQL\"}");
        when(sqlRepo.findByFlightIdOrderByGeneratedDateDesc("TK1001")).thenReturn(Collections.singletonList(entity));

        ResponseEntity<?> response = rosterController.generateRoster("TK1001", false);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(mongoRepo, never()).findByFlightIdOrderByGeneratedDateDesc(anyString());
    }

    @Test
    void testGenerateRoster_FoundInSql_BackupScan() {
        when(sqlRepo.findByFlightIdOrderByGeneratedDateDesc("TK1001")).thenReturn(Collections.emptyList());
        
        RosterSqlEntity entity = new RosterSqlEntity();
        entity.setFlightId("TK1001");
        entity.setRosterData("{\"flightId\": \"TK1001\", \"data\": \"backup\"}");
        when(sqlRepo.findAll()).thenReturn(Collections.singletonList(entity));

        ResponseEntity<?> response = rosterController.generateRoster("TK1001", false);
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void testGenerateRoster_FoundInMongo() {
        when(sqlRepo.findByFlightIdOrderByGeneratedDateDesc(anyString())).thenReturn(Collections.emptyList());
        when(sqlRepo.findAll()).thenReturn(Collections.emptyList());

        RosterDocument doc = new RosterDocument();
        doc.setRosterData(new HashMap<>());
        when(mongoRepo.findByFlightIdOrderByGeneratedDateDesc("TK1001")).thenReturn(Collections.singletonList(doc));

        ResponseEntity<?> response = rosterController.generateRoster("TK1001", false);
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void testGenerateRoster_FallbackToLocalhost() {
        // Since forceNew=true, SQL check is skipped
        
        when(restTemplate.getForObject(contains("flight-info-api"), eq(Map.class)))
            .thenThrow(new RuntimeException("Service down"));
        
        when(restTemplate.getForObject(contains("localhost"), eq(Map.class)))
            .thenReturn(validFlight);

        ResponseEntity<?> response = rosterController.generateRoster("TK1001", true);
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void testSaveRoster_Sql_Success() {
        Map<String, Object> data = new HashMap<>();
        data.put("flightId", "TK1001");
        data.put("pilots", List.of(Map.of("name", "P1"), Map.of("name", "P2")));

        when(sqlRepo.findByFlightIdOrderByGeneratedDateDesc("TK1001")).thenReturn(new ArrayList<>());
        ResponseEntity<?> response = rosterController.saveRoster(data, "sql");
        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(sqlRepo).save(any(RosterSqlEntity.class));
    }

    @Test
    void testSaveRoster_Mongo_UpdateExisting() {
        Map<String, Object> data = new HashMap<>();
        data.put("flightId", "TK1001");
        data.put("pilots", List.of(Map.of("name", "P1"), Map.of("name", "P2")));

        RosterDocument existingDoc = new RosterDocument();
        existingDoc.setId("existing_id");
        when(mongoRepo.findByFlightIdOrderByGeneratedDateDesc("TK1001")).thenReturn(Collections.singletonList(existingDoc));

        ResponseEntity<?> response = rosterController.saveRoster(data, "mongo");
        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(mongoRepo).save(existingDoc);
    }

    @Test
    void testGetFlights_MergeLogic() {
        when(restTemplate.getForObject(anyString(), eq(Map[].class))).thenReturn(new Map[]{validFlight});
        
        RosterSqlEntity dbEntity = new RosterSqlEntity();
        dbEntity.setRosterData("{\"flightInfo\": {\"flightNumber\": \"TK9999\"}}");
        when(sqlRepo.findAll()).thenReturn(Collections.singletonList(dbEntity));

        List<Map<String, Object>> result = rosterController.getFlights();
        assertTrue(result.size() >= 1);
    }

    @Test
    void testSmartSeatAssignment_Complex() {
        lenient().when(restTemplate.getForObject(anyString(), eq(Map.class))).thenReturn(validFlight);
        
        Map<String, Object> mother = new HashMap<>(); 
        mother.put("id", 1); mother.put("name", "M"); mother.put("seatType", "ECONOMY"); mother.put("affiliatedPassengerIds", List.of(2));
        
        Map<String, Object> child = new HashMap<>(); 
        child.put("id", 2); child.put("name", "C"); child.put("seatType", "ECONOMY"); child.put("affiliatedPassengerIds", List.of(1));
        
        Map<String, Object> busPax = new HashMap<>(); 
        busPax.put("id", 3); busPax.put("name", "B"); busPax.put("seatType", "BUSINESS");
        
        Map<String, Object> infant = new HashMap<>(); 
        infant.put("id", 4); infant.put("name", "I"); infant.put("age", 1);

        Map[] passengers = {mother, child, busPax, infant};
        lenient().when(restTemplate.getForObject(contains("passengers"), eq(Map[].class))).thenReturn(passengers);

        ResponseEntity<?> response = rosterController.generateRoster("TK1001", true);
        
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        List<Map> resultPassengers = (List<Map>) body.get("passengers");
        assertNotNull(resultPassengers);
    }

    @Test
    void testSecurityConfigBeans() {
        SecurityConfig config = new SecurityConfig();
        assertNotNull(config.userDetailsService());
        assertNotNull(config.corsConfigurationSource());
    }

    @Test
    void testEntities() {
        RosterSqlEntity e = new RosterSqlEntity();
        e.setId(1L);
        e.setFlightId("F1");
        e.setGeneratedDate(new Date());
        e.setRosterData("{}");
        assertNotNull(e.getId());
        assertEquals("F1", e.getFlightId());
        assertNotNull(e.getGeneratedDate());
        assertEquals("{}", e.getRosterData());

        RosterDocument d = new RosterDocument();
        d.setId("1");
        d.setFlightId("F1");
        d.setGeneratedDate(new Date());
        d.setRosterData(new HashMap<>());
        assertNotNull(d.getId());
        assertEquals("F1", d.getFlightId());
    }
    
    @Test
    void testMainApp() {
        MainSystemApi api = new MainSystemApi();
        assertNotNull(api);
    }

    // --- Extended Coverage Tests ---

    @Test
    void testGetCandidatePilots_Exception() {
        when(restTemplate.getForObject(contains("pilots"), eq(Map[].class))).thenThrow(new RuntimeException("API Down"));
        List<Map> result = rosterController.getCandidatePilots("Boeing", null, null);
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void testGetCandidateCrew_Exception() {
        when(restTemplate.getForObject(contains("cabin-crew"), eq(Map[].class))).thenThrow(new RuntimeException("API Down"));
        List<Map> result = rosterController.getCandidateCrew(null, null);
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void testSaveRoster_ValidationFail_Pilots() {
        // Should fail if pilot list is empty/missing
        Map<String, Object> data = new HashMap<>();
        ResponseEntity<?> response = rosterController.saveRoster(data, "sql");
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertTrue(response.getBody().toString().contains("Validation Error"));
    }

    @Test
    void testSaveRoster_MissingFlightId() {
        // Should fail if pilots exist but ID is missing
        Map<String, Object> data = new HashMap<>();
        data.put("pilots", List.of(Map.of("name", "P1"), Map.of("name", "P2")));
        
        ResponseEntity<?> response = rosterController.saveRoster(data, "sql");
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertTrue(response.getBody().toString().contains("Validation Error"));
    }

    @Test
    void testSaveRoster_InternalServerError() {
        Map<String, Object> data = new HashMap<>();
        data.put("flightId", "TK1001");
        data.put("pilots", List.of(Map.of("name", "P1"), Map.of("name", "P2")));

        // Simulate DB failure
        when(sqlRepo.findByFlightIdOrderByGeneratedDateDesc(anyString())).thenThrow(new RuntimeException("DB Fail"));

        ResponseEntity<?> response = rosterController.saveRoster(data, "sql");
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
    }

    @Test
    void testGenerateRoster_FlightNotFound_FallbackFail() {
        when(restTemplate.getForObject(contains("flights/"), eq(Map.class))).thenThrow(new RuntimeException("Main Fail"));
        when(restTemplate.getForObject(contains("localhost"), eq(Map.class))).thenThrow(new RuntimeException("Localhost Fail"));

        ResponseEntity<?> response = rosterController.generateRoster("TK9999", true);
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    void testGenerateRoster_SharedInfo() {
        lenient().when(restTemplate.getForObject(contains("flights/"), eq(Map.class))).thenReturn(validFlight);
        
        Map<String, Object> sharedInfo = new HashMap<>();
        sharedInfo.put("partnerCompanyName", "Lufthansa");
        when(restTemplate.getForObject(contains("shared-info"), eq(Map.class))).thenReturn(sharedInfo);

        ResponseEntity<?> response = rosterController.generateRoster("TK1001", true);
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        Map<String, Object> flightInfo = (Map<String, Object>) body.get("flightInfo");
        
        assertNotNull(flightInfo.get("sharedDetails"));
    }

    @Test
    void testGenerateRoster_NormalizationRetry() {
        // Fail on space-containing ID
        when(restTemplate.getForObject(contains("TK 1001"), eq(Map.class)))
            .thenThrow(new RuntimeException("Not Found"));
        
        // Succeed on normalized ID
        when(restTemplate.getForObject(contains("TK1001"), eq(Map.class)))
            .thenReturn(validFlight);

        ResponseEntity<?> response = rosterController.generateRoster("TK 1001", true);
        
        assertEquals(HttpStatus.OK, response.getStatusCode());
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertEquals("TK1001", body.get("flightId"));
    }

    @Test
    void testGenerateRoster_ChefMenu() {
        lenient().when(restTemplate.getForObject(contains("flights/"), eq(Map.class))).thenReturn(validFlight);
        
        Map<String, Object> chef = new HashMap<>();
        chef.put("type", "CHEF");
        chef.put("allowedVehicles", List.of("Embraer E195"));
        chef.put("chefRecipes", List.of("Special Pasta"));
        
        Map[] crew = {chef};
        when(restTemplate.getForObject(contains("cabin-crew"), eq(Map[].class))).thenReturn(crew);

        ResponseEntity<?> response = rosterController.generateRoster("TK1001", true);
        
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        List<String> menu = (List<String>) body.get("menu");
        
        assertTrue(menu.stream().anyMatch(m -> m.contains("Special Pasta")));
    }

    @Test
    void testSmartSeatAssignment_BadConfig() {
        Map<String, Object> badFlight = new HashMap<>(validFlight);
        Map<String, Object> badVehicle = new HashMap<>(vehicleType);
        badVehicle.put("seatingPlanConfig", "{ invalid_json }");
        badFlight.put("vehicleType", badVehicle);

        lenient().when(restTemplate.getForObject(contains("flights/"), eq(Map.class))).thenReturn(badFlight);
        
        Map<String, Object> p1 = new HashMap<>(); p1.put("id", 1); p1.put("seatType", "ECONOMY");
        Map[] passengers = {p1};
        lenient().when(restTemplate.getForObject(contains("passengers"), eq(Map[].class))).thenReturn(passengers);

        ResponseEntity<?> response = rosterController.generateRoster("TK1001", true);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        List<Map> resPass = (List<Map>) body.get("passengers");
        assertNull(resPass.get(0).get("seatNumber")); 
    }

    @Test
    void testGenerateRoster_NullServiceResponse() {
        lenient().when(restTemplate.getForObject(contains("flights/"), eq(Map.class))).thenReturn(validFlight);
        lenient().when(restTemplate.getForObject(contains("pilots"), eq(Map[].class))).thenReturn(null);
        lenient().when(restTemplate.getForObject(contains("cabin-crew"), eq(Map[].class))).thenReturn(null);
        lenient().when(restTemplate.getForObject(contains("passengers"), eq(Map[].class))).thenReturn(null);

        ResponseEntity<?> response = rosterController.generateRoster("TK1001", true);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertTrue(((List)body.get("pilots")).isEmpty());
    }
}