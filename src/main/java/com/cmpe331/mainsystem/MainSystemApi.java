package com.cmpe331.mainsystem;

import jakarta.persistence.*;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.ResponseEntity;
import com.fasterxml.jackson.databind.ObjectMapper; // JSON Parsing

import java.util.*;
import java.util.stream.Collectors;

import static org.springframework.security.config.Customizer.withDefaults;

@SpringBootApplication
public class MainSystemApi {

    public static void main(String[] args) {
        System.setProperty("server.port", "8080");
        System.setProperty("spring.datasource.url", "jdbc:sqlite:mainsystem_db.sqlite");
        
        String mongoHost = System.getenv("MONGO_HOST");
        if (mongoHost == null) mongoHost = "localhost";
        System.setProperty("spring.data.mongodb.uri", "mongodb://" + mongoHost + ":27017/flight_roster_nosql");

        SpringApplication.run(MainSystemApi.class, args);
    }

    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder.basicAuthentication("admin", "password").build();
    }
}

@Configuration
@EnableWebSecurity
class SecurityConfig {
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.csrf(c -> c.disable())
            .authorizeHttpRequests(a -> a.anyRequest().authenticated())
            .httpBasic(withDefaults());
        return http.build();
    }
    @Bean
    public InMemoryUserDetailsManager userDetailsService() {
        UserDetails user = User.withDefaultPasswordEncoder()
            .username("admin").password("password").roles("USER").build();
        return new InMemoryUserDetailsManager(user);
    }
}

@RestController
@RequestMapping("/api/roster")
@CrossOrigin(origins = "*")
class RosterController {

    private final RestTemplate restTemplate;
    private final RosterSqlRepository sqlRepo;
    private final RosterMongoRepository mongoRepo;
    private final String FLIGHT_API, PILOT_API, CREW_API, PASSENGER_API;

    public RosterController(RestTemplate restTemplate, RosterSqlRepository sqlRepo, RosterMongoRepository mongoRepo) {
        this.restTemplate = restTemplate;
        this.sqlRepo = sqlRepo;
        this.mongoRepo = mongoRepo;
        String flightHost = System.getenv("FLIGHT_API_HOST");
        this.FLIGHT_API = "http://" + (flightHost != null ? flightHost : "localhost") + ":8081/api/flights";
        String pilotHost = System.getenv("PILOT_API_HOST");
        this.PILOT_API = "http://" + (pilotHost != null ? pilotHost : "localhost") + ":8082/api/pilots";
        String crewHost = System.getenv("CREW_API_HOST");
        this.CREW_API = "http://" + (crewHost != null ? crewHost : "localhost") + ":8083/api/cabin-crew";
        String passHost = System.getenv("PASSENGER_API_HOST");
        this.PASSENGER_API = "http://" + (passHost != null ? passHost : "localhost") + ":8084/api/passengers";
    }

    @GetMapping("/flights")
    public ResponseEntity<?> getAllFlights() {
        try {
            List<Map> flights = Arrays.asList(restTemplate.getForObject(FLIGHT_API, Map[].class));
            return ResponseEntity.ok(flights);
        } catch (Exception e) { return ResponseEntity.status(503).body("Error connecting to Flight Service."); }
    }

    @GetMapping("/candidates/pilots/{vehicleType}")
    public ResponseEntity<?> getCandidatePilots(@PathVariable("vehicleType") String vehicleType) {
        try { return ResponseEntity.ok(restTemplate.getForObject(PILOT_API + "/vehicle/" + vehicleType, Map[].class)); } 
        catch (Exception e) { return ResponseEntity.status(503).body("Error fetching pilots."); }
    }

    @GetMapping("/candidates/crew")
    public ResponseEntity<?> getCandidateCrew() {
        try { return ResponseEntity.ok(restTemplate.getForObject(CREW_API, Map[].class)); }
        catch (Exception e) { return ResponseEntity.status(503).body("Error fetching crew."); }
    }

    @GetMapping("/generate/{flightId}")
    public ResponseEntity<?> generateRoster(@PathVariable("flightId") String flightId, 
                                          @RequestParam(value = "dbType", defaultValue = "sql") String dbType) {
        try {
            // 1. Fetch Data
            Map flight = restTemplate.getForObject(FLIGHT_API + "/" + flightId, Map.class);
            if (flight == null) return ResponseEntity.badRequest().body("Flight not found!");
            
            String vehicleType = "Unknown";
            Map vehicleTypeObj = (Map) flight.get("vehicleType");
            if (vehicleTypeObj != null) vehicleType = (String) vehicleTypeObj.get("modelName");

            List<Map<String, Object>> passengers = new ArrayList<>(Arrays.asList(restTemplate.getForObject(PASSENGER_API + "/flight/" + flightId, Map[].class)));

            // 2. LOGIC: Assign Seats to Passengers (New Feature)
            assignSeats(passengers, vehicleTypeObj);

            // 3. Prepare Response (Pilots/Crew empty for manual assignment)
            Map<String, Object> responseData = new LinkedHashMap<>();
            responseData.put("flightId", flightId);
            responseData.put("generatedDate", new Date());
            responseData.put("flightInfo", flight);
            responseData.put("pilots", new ArrayList<>());
            responseData.put("cabinCrew", new ArrayList<>());
            responseData.put("passengers", passengers);

            // 4. Save
            if (dbType.equalsIgnoreCase("nosql")) {
                RosterDocument doc = new RosterDocument(); doc.setFlightId(flightId); doc.setGeneratedDate(new Date()); doc.setRosterData(responseData);
                mongoRepo.save(doc); responseData.put("storageMethod", "NoSQL (MongoDB)");
            } else {
                RosterSqlEntity entity = new RosterSqlEntity(); entity.setFlightId(flightId); entity.setGeneratedDate(new Date()); entity.setRosterData(responseData.toString());
                sqlRepo.save(entity); responseData.put("storageMethod", "SQL (SQLite)");
            }
            return ResponseEntity.ok(responseData);

        } catch (Exception e) { e.printStackTrace(); return ResponseEntity.internalServerError().body("Error: " + e.getMessage()); }
    }

    // --- SEAT ASSIGNMENT LOGIC ---
    private void assignSeats(List<Map<String, Object>> passengers, Map vehicleType) {
        if (vehicleType == null) return;
        
        // Parse config (e.g. {"rows":30, "layout":"3-3"})
        int totalRows = 30; 
        try {
            String configStr = (String) vehicleType.get("seatingPlanConfig");
            Map<String, Object> config = new ObjectMapper().readValue(configStr, Map.class);
            totalRows = (int) config.get("rows");
        } catch (Exception e) { System.err.println("Could not parse seat config, using default 30 rows."); }

        Set<String> occupiedSeats = new HashSet<>();
        List<Map<String, Object>> unassignedPassengers = new ArrayList<>();

        // 1. Identify occupied seats and unassigned people
        for (Map<String, Object> p : passengers) {
            String seat = (String) p.get("seatNumber");
            Integer age = (Integer) p.get("age");
            
            // Infants don't get seats
            if (age != null && age <= 2) {
                p.put("seatNumber", null); 
                continue;
            }

            if (seat != null && !seat.isEmpty()) {
                occupiedSeats.add(seat);
            } else {
                unassignedPassengers.add(p);
            }
        }

        // 2. Assign seats to the rest (Greedy + Affiliation Grouping)
        // Group by affiliation first (simple heuristic: sort by ID to keep groups close in list)
        // Ideally we'd use a graph, but simple list processing is enough for MVP
        
        String[] cols = {"A", "B", "C", "D", "E", "F"};
        int currentRow = 1;
        int currentColIdx = 0;

        for (Map<String, Object> p : unassignedPassengers) {
            // Find next empty seat
            while (true) {
                if (currentRow > totalRows) {
                    p.put("seatNumber", "STANDBY"); // Plane full
                    break;
                }
                
                String candidateSeat = currentRow + cols[currentColIdx];
                if (!occupiedSeats.contains(candidateSeat)) {
                    p.put("seatNumber", candidateSeat);
                    occupiedSeats.add(candidateSeat);
                    
                    // Move to next seat for next person
                    currentColIdx++;
                    if (currentColIdx >= cols.length) {
                        currentColIdx = 0;
                        currentRow++;
                    }
                    break; // Assigned!
                }
                
                // Seat occupied, check next
                currentColIdx++;
                if (currentColIdx >= cols.length) {
                    currentColIdx = 0;
                    currentRow++;
                }
            }
        }
    }
}

// Entities (Same as before)
@Entity @Table(name = "rosters") class RosterSqlEntity { @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id; private String flightId; private Date generatedDate; @Lob @Column(length = 10000) private String rosterData; public Long getId() { return id; } public void setFlightId(String flightId) { this.flightId = flightId; } public void setGeneratedDate(Date generatedDate) { this.generatedDate = generatedDate; } public void setRosterData(String rosterData) { this.rosterData = rosterData; } }
interface RosterSqlRepository extends JpaRepository<RosterSqlEntity, Long> {}
@Document(collection = "rosters") class RosterDocument { @org.springframework.data.annotation.Id private String id; private String flightId; private Date generatedDate; private Map<String, Object> rosterData; public void setFlightId(String flightId) { this.flightId = flightId; } public void setGeneratedDate(Date generatedDate) { this.generatedDate = generatedDate; } public void setRosterData(Map<String, Object> rosterData) { this.rosterData = rosterData; } }
interface RosterMongoRepository extends MongoRepository<RosterDocument, String> {}