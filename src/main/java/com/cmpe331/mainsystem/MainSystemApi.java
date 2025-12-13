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

import java.util.*;

import static org.springframework.security.config.Customizer.withDefaults;

@SpringBootApplication
public class MainSystemApi {

    public static void main(String[] args) {
        System.setProperty("server.port", "8080");
        System.setProperty("spring.datasource.url", "jdbc:sqlite:mainsystem_db.sqlite");
        
        // Docker/Local MongoDB Switching
        String mongoHost = System.getenv("MONGO_HOST");
        if (mongoHost == null) mongoHost = "localhost";
        System.setProperty("spring.data.mongodb.uri", "mongodb://" + mongoHost + ":27017/flight_roster_nosql");

        SpringApplication.run(MainSystemApi.class, args);
    }

    /**
     * AUTHENTICATED REST TEMPLATE
     * This is the key to Inter-Service Security.
     * It automatically attaches "Basic admin:password" header to every request
     * sent to Flight, Pilot, Crew, and Passenger APIs.
     */
    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder
                .basicAuthentication("admin", "password")
                .build();
    }
}

/**
 * SECURITY CONFIGURATION
 * Protects the Main System itself.
 */
@Configuration
@EnableWebSecurity
class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth
                .anyRequest().authenticated() // Lock everything
            )
            .httpBasic(withDefaults()); // Use Login Pop-up
        return http.build();
    }

    @Bean
    public InMemoryUserDetailsManager userDetailsService() {
        UserDetails user = User.withDefaultPasswordEncoder()
            .username("admin")
            .password("password")
            .roles("USER")
            .build();
        return new InMemoryUserDetailsManager(user);
    }
}

// ------------------- CONTROLLER -------------------

@RestController
@RequestMapping("/api/roster")
@CrossOrigin(origins = "*")
class RosterController {

    private final RestTemplate restTemplate;
    private final RosterSqlRepository sqlRepo;
    private final RosterMongoRepository mongoRepo;

    // Service URLs (Docker or Local)
    private final String FLIGHT_API;
    private final String PILOT_API;
    private final String CREW_API;
    private final String PASSENGER_API;

    public RosterController(RestTemplate restTemplate, RosterSqlRepository sqlRepo, RosterMongoRepository mongoRepo) {
        this.restTemplate = restTemplate;
        this.sqlRepo = sqlRepo;
        this.mongoRepo = mongoRepo;

        // Config logic to find other services
        String flightHost = System.getenv("FLIGHT_API_HOST");
        this.FLIGHT_API = "http://" + (flightHost != null ? flightHost : "localhost") + ":8081/api/flights";

        String pilotHost = System.getenv("PILOT_API_HOST");
        this.PILOT_API = "http://" + (pilotHost != null ? pilotHost : "localhost") + ":8082/api/pilots";

        String crewHost = System.getenv("CREW_API_HOST");
        this.CREW_API = "http://" + (crewHost != null ? crewHost : "localhost") + ":8083/api/cabin-crew";

        String passHost = System.getenv("PASSENGER_API_HOST");
        this.PASSENGER_API = "http://" + (passHost != null ? passHost : "localhost") + ":8084/api/passengers";
    }

    @GetMapping("/generate/{flightId}")
    public ResponseEntity<?> generateRoster(@PathVariable("flightId") String flightId, 
                                          @RequestParam(value = "dbType", defaultValue = "sql") String dbType) {
        try {
            System.out.println("--- Starting Roster Generation for " + flightId + " ---");

            // 1. Fetch Flight
            Map flight;
            try {
                flight = restTemplate.getForObject(FLIGHT_API + "/" + flightId, Map.class);
            } catch (Exception e) { 
                return ResponseEntity.status(503).body("Error connecting to Flight API. Check Auth or Docker."); 
            }
            if (flight == null) return ResponseEntity.badRequest().body("Flight not found!");
            
            String vehicleType = "Unknown";
            if (flight.get("vehicleType") instanceof Map) {
                vehicleType = (String) ((Map) flight.get("vehicleType")).get("modelName");
            }

            // 2. Fetch Passengers
            List<Map> passengers;
            try {
                passengers = Arrays.asList(restTemplate.getForObject(PASSENGER_API + "/flight/" + flightId, Map[].class));
            } catch (Exception e) { return ResponseEntity.status(503).body("Error connecting to Passenger API."); }

            // 3. Fetch Pilots
            List<Map> selectedPilots = new ArrayList<>();
            try {
                Map[] allPilots = restTemplate.getForObject(PILOT_API + "/vehicle/" + vehicleType, Map[].class);
                if (allPilots != null && allPilots.length >= 2) selectedPilots = Arrays.asList(Arrays.copyOfRange(allPilots, 0, 2));
            } catch (Exception e) { return ResponseEntity.status(503).body("Error connecting to Pilot API."); }

            // 4. Fetch Crew
            List<Map> selectedCrew = new ArrayList<>();
            try {
                Map[] allCrew = restTemplate.getForObject(CREW_API, Map[].class);
                if (allCrew != null && allCrew.length >= 3) selectedCrew = Arrays.asList(Arrays.copyOfRange(allCrew, 0, 3));
            } catch (Exception e) { return ResponseEntity.status(503).body("Error connecting to Crew API."); }

            // 5. Build Response
            Map<String, Object> responseData = new LinkedHashMap<>();
            responseData.put("flightId", flightId);
            responseData.put("generatedDate", new Date());
            responseData.put("flightInfo", flight);
            responseData.put("pilots", selectedPilots);
            responseData.put("cabinCrew", selectedCrew);
            responseData.put("passengers", passengers);

            // 6. Save Logic
            if (dbType.equalsIgnoreCase("nosql")) {
                RosterDocument doc = new RosterDocument();
                doc.setFlightId(flightId);
                doc.setGeneratedDate(new Date());
                doc.setRosterData(responseData);
                mongoRepo.save(doc);
                responseData.put("storageMethod", "NoSQL (MongoDB)");
            } else {
                RosterSqlEntity entity = new RosterSqlEntity();
                entity.setFlightId(flightId);
                entity.setGeneratedDate(new Date());
                entity.setRosterData(responseData.toString());
                sqlRepo.save(entity);
                responseData.put("storageMethod", "SQL (SQLite)");
            }

            return ResponseEntity.ok(responseData);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().body("System Error: " + e.getMessage());
        }
    }
}

// SQL Components
@Entity @Table(name = "rosters")
class RosterSqlEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    private String flightId;
    private Date generatedDate;
    @Lob @Column(length = 10000) private String rosterData;
    public Long getId() { return id; }
    public void setFlightId(String flightId) { this.flightId = flightId; }
    public void setGeneratedDate(Date generatedDate) { this.generatedDate = generatedDate; }
    public void setRosterData(String rosterData) { this.rosterData = rosterData; }
}
interface RosterSqlRepository extends JpaRepository<RosterSqlEntity, Long> {}

// NoSQL Components
@Document(collection = "rosters")
class RosterDocument {
    @org.springframework.data.annotation.Id private String id;
    private String flightId;
    private Date generatedDate;
    private Map<String, Object> rosterData;
    public void setFlightId(String flightId) { this.flightId = flightId; }
    public void setGeneratedDate(Date generatedDate) { this.generatedDate = generatedDate; }
    public void setRosterData(Map<String, Object> rosterData) { this.rosterData = rosterData; }
}
interface RosterMongoRepository extends MongoRepository<RosterDocument, String> {}