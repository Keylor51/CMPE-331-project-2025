package com.cmpe331.mainsystem;

import jakarta.persistence.*;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.ResponseEntity;

import java.util.*;

@SpringBootApplication
public class MainSystemApi {

    public static void main(String[] args) {
        // Run Main System on Port 8080
        System.setProperty("server.port", "8080");
        // SQL DB Config
        System.setProperty("spring.datasource.url", "jdbc:sqlite:mainsystem_db.sqlite");
        // NoSQL DB Config (MongoDB defaults to localhost:27017)
        System.setProperty("spring.data.mongodb.database", "flight_roster_nosql");
        
        SpringApplication.run(MainSystemApi.class, args);
    }

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}

@RestController
@RequestMapping("/api/roster")
@CrossOrigin(origins = "*")
class RosterController {

    private final RestTemplate restTemplate;
    private final RosterSqlRepository sqlRepo;
    private final RosterMongoRepository mongoRepo;

    // Service URLs
    private final String FLIGHT_API = "http://localhost:8081/api/flights";
    private final String PILOT_API = "http://localhost:8082/api/pilots";
    private final String CREW_API = "http://localhost:8083/api/cabin-crew";
    private final String PASSENGER_API = "http://localhost:8084/api/passengers";

    public RosterController(RestTemplate restTemplate, RosterSqlRepository sqlRepo, RosterMongoRepository mongoRepo) {
        this.restTemplate = restTemplate;
        this.sqlRepo = sqlRepo;
        this.mongoRepo = mongoRepo;
    }

    // New Parameter: dbType (sql or nosql)
    @GetMapping("/generate/{flightId}")
    public ResponseEntity<?> generateRoster(@PathVariable("flightId") String flightId, 
                                          @RequestParam(value = "dbType", defaultValue = "sql") String dbType) {
        try {
            System.out.println("--- Starting Roster Generation for " + flightId + " (Saving to: " + dbType + ") ---");

            // --- 1. Fetch Data from Microservices ---
            
            // Fetch Flight
            Map flight;
            try {
                flight = restTemplate.getForObject(FLIGHT_API + "/" + flightId, Map.class);
            } catch (Exception e) { return ResponseEntity.status(503).body("Error connecting to Flight API (8081)."); }
            if (flight == null) return ResponseEntity.badRequest().body("Flight not found!");
            
            String vehicleType = "Unknown";
            if (flight.get("vehicleType") instanceof Map) {
                vehicleType = (String) ((Map) flight.get("vehicleType")).get("modelName");
            }

            // Fetch Passengers
            List<Map> passengers;
            try {
                passengers = Arrays.asList(restTemplate.getForObject(PASSENGER_API + "/flight/" + flightId, Map[].class));
            } catch (Exception e) { return ResponseEntity.status(503).body("Error connecting to Passenger API (8084)."); }

            // Fetch Pilots
            List<Map> selectedPilots = new ArrayList<>();
            try {
                Map[] allPilots = restTemplate.getForObject(PILOT_API + "/vehicle/" + vehicleType, Map[].class);
                if (allPilots != null && allPilots.length >= 2) selectedPilots = Arrays.asList(Arrays.copyOfRange(allPilots, 0, 2));
            } catch (Exception e) { return ResponseEntity.status(503).body("Error connecting to Pilot API (8082)."); }

            // Fetch Crew
            List<Map> selectedCrew = new ArrayList<>();
            try {
                Map[] allCrew = restTemplate.getForObject(CREW_API, Map[].class);
                if (allCrew != null && allCrew.length >= 3) selectedCrew = Arrays.asList(Arrays.copyOfRange(allCrew, 0, 3));
            } catch (Exception e) { return ResponseEntity.status(503).body("Error connecting to Crew API (8083)."); }

            // --- 2. Build Response Object ---
            Map<String, Object> responseData = new LinkedHashMap<>();
            responseData.put("flightId", flightId);
            responseData.put("generatedDate", new Date());
            responseData.put("flightInfo", flight);
            responseData.put("pilots", selectedPilots);
            responseData.put("cabinCrew", selectedCrew);
            responseData.put("passengers", passengers);

            // --- 3. Save Logic (The Switch) ---
            if (dbType.equalsIgnoreCase("nosql")) {
                // SAVE TO MONGODB
                RosterDocument doc = new RosterDocument();
                doc.setFlightId(flightId);
                doc.setGeneratedDate(new Date());
                doc.setRosterData(responseData); // Mongo can store the Map directly as JSON!
                mongoRepo.save(doc);
                System.out.println("Saved to MongoDB!");
                responseData.put("storageMethod", "NoSQL (MongoDB)");
            } else {
                // SAVE TO SQLITE
                RosterSqlEntity entity = new RosterSqlEntity();
                entity.setFlightId(flightId);
                entity.setGeneratedDate(new Date());
                entity.setRosterData(responseData.toString()); // SQL needs stringified JSON
                sqlRepo.save(entity);
                System.out.println("Saved to SQLite!");
                responseData.put("storageMethod", "SQL (SQLite)");
            }

            return ResponseEntity.ok(responseData);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().body("System Error: " + e.getMessage());
        }
    }
}

// ------------------- SQL (SQLite) COMPONENTS -------------------
@Entity
@Table(name = "rosters")
class RosterSqlEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String flightId;
    private Date generatedDate;
    @Lob @Column(length = 10000)
    private String rosterData;

    public Long getId() { return id; }
    public void setFlightId(String flightId) { this.flightId = flightId; }
    public void setGeneratedDate(Date generatedDate) { this.generatedDate = generatedDate; }
    public void setRosterData(String rosterData) { this.rosterData = rosterData; }
}

interface RosterSqlRepository extends JpaRepository<RosterSqlEntity, Long> {}

// ------------------- NoSQL (MongoDB) COMPONENTS -------------------
@Document(collection = "rosters")
class RosterDocument {
    @org.springframework.data.annotation.Id
    private String id;
    private String flightId;
    private Date generatedDate;
    private Map<String, Object> rosterData; // Native JSON structure

    public void setFlightId(String flightId) { this.flightId = flightId; }
    public void setGeneratedDate(Date generatedDate) { this.generatedDate = generatedDate; }
    public void setRosterData(Map<String, Object> rosterData) { this.rosterData = rosterData; }
}

interface RosterMongoRepository extends MongoRepository<RosterDocument, String> {}