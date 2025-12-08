package com.cmpe331.mainsystem;

import jakarta.persistence.*;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.ResponseEntity;

import java.util.*;

@SpringBootApplication
public class MainSystemApi {

    public static void main(String[] args) {
        // Run Main System on Port 8080
        System.setProperty("server.port", "8080");
        System.setProperty("spring.datasource.url", "jdbc:sqlite:mainsystem_db.sqlite");
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
    private final RosterRepository rosterRepo;

    // Service URLs
    private final String FLIGHT_API = "http://localhost:8081/api/flights";
    private final String PILOT_API = "http://localhost:8082/api/pilots";
    private final String CREW_API = "http://localhost:8083/api/cabin-crew";
    private final String PASSENGER_API = "http://localhost:8084/api/passengers";

    public RosterController(RestTemplate restTemplate, RosterRepository rosterRepo) {
        this.restTemplate = restTemplate;
        this.rosterRepo = rosterRepo;
    }

    @GetMapping("/generate/{flightId}")
    public ResponseEntity<?> generateRoster(@PathVariable("flightId") String flightId) {
        try {
            System.out.println("--- Starting Roster Generation for " + flightId + " ---");

            // 1. Fetch Flight
            Map flight;
            try {
                flight = restTemplate.getForObject(FLIGHT_API + "/" + flightId, Map.class);
            } catch (Exception e) {
                return ResponseEntity.status(503).body("Error connecting to Flight API (8081). Is it running?");
            }
            
            if (flight == null) return ResponseEntity.badRequest().body("Flight not found!");
            
            // Extract Vehicle Type Name safely
            String vehicleType = "Unknown";
            if (flight.get("vehicleType") instanceof Map) {
                vehicleType = (String) ((Map) flight.get("vehicleType")).get("modelName");
            }

            // 2. Fetch Passengers
            List<Map> passengers;
            try {
                passengers = Arrays.asList(restTemplate.getForObject(PASSENGER_API + "/flight/" + flightId, Map[].class));
            } catch (Exception e) {
                return ResponseEntity.status(503).body("Error connecting to Passenger API (8084). Is it running?");
            }

            // 3. Fetch Pilots
            List<Map> selectedPilots = new ArrayList<>();
            try {
                Map[] allPilots = restTemplate.getForObject(PILOT_API + "/vehicle/" + vehicleType, Map[].class);
                if (allPilots != null && allPilots.length >= 2) {
                    selectedPilots = Arrays.asList(Arrays.copyOfRange(allPilots, 0, 2)); // Pick first 2
                }
            } catch (Exception e) {
                return ResponseEntity.status(503).body("Error connecting to Pilot API (8082). Is it running?");
            }

            // 4. Fetch Crew
            List<Map> selectedCrew = new ArrayList<>();
            try {
                Map[] allCrew = restTemplate.getForObject(CREW_API, Map[].class);
                if (allCrew != null && allCrew.length >= 3) {
                    selectedCrew = Arrays.asList(Arrays.copyOfRange(allCrew, 0, 3)); // Pick first 3
                }
            } catch (Exception e) {
                return ResponseEntity.status(503).body("Error connecting to Crew API (8083). Is it running?");
            }

            // 5. Build Response
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("flightId", flightId);
            response.put("generatedDate", new Date());
            response.put("flightInfo", flight);
            response.put("pilots", selectedPilots);
            response.put("cabinCrew", selectedCrew);
            response.put("passengers", passengers);

            // Save to DB (Optional for MVP, but good for requirements)
            Roster roster = new Roster();
            roster.setFlightId(flightId);
            roster.setGeneratedDate(new Date());
            roster.setRosterData(response.toString());
            rosterRepo.save(roster);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().body("System Error: " + e.getMessage());
        }
    }
}

@Entity
@Table(name = "rosters")
class Roster {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String flightId;
    private Date generatedDate;

    @Lob
    @Column(length = 10000)
    private String rosterData; // Storing the whole thing as a string for simplicity

    public Long getId() { return id; }
    public void setFlightId(String flightId) { this.flightId = flightId; }
    public void setGeneratedDate(Date generatedDate) { this.generatedDate = generatedDate; }
    public void setRosterData(String rosterData) { this.rosterData = rosterData; }
}

interface RosterRepository extends JpaRepository<Roster, Long> {}