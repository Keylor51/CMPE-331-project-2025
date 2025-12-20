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
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.http.HttpMethod; 
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;

import java.util.*;
import java.time.Duration;
import java.util.stream.Collectors;

import static org.springframework.security.config.Customizer.withDefaults;

@SpringBootApplication
public class MainSystemApi {

    public static void main(String[] args) {
        System.setProperty("server.port", "8080");
        System.setProperty("spring.datasource.url", "jdbc:sqlite:roster_db.sqlite");
        String mongoEnv = System.getenv("SPRING_DATA_MONGODB_URI");
        if (mongoEnv == null || mongoEnv.isEmpty()) {
            System.setProperty("spring.data.mongodb.uri", "mongodb://localhost:27017/rosterdb");
        }
        SpringApplication.run(MainSystemApi.class, args);
    }

    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder
                .basicAuthentication("admin", "password")
                .setConnectTimeout(Duration.ofSeconds(5))
                .setReadTimeout(Duration.ofSeconds(10))
                .build();
    }
}

@Configuration
@EnableWebSecurity
class SecurityConfig {
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.csrf(c -> c.disable())
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.POST, "/api/roster/save").hasRole("ADMIN")
                .requestMatchers("/api/roster/**").hasAnyRole("ADMIN", "READER")
                .anyRequest().authenticated())
            .httpBasic(withDefaults());
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of("*"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    public InMemoryUserDetailsManager userDetailsService() {
        return new InMemoryUserDetailsManager(
            User.withDefaultPasswordEncoder().username("admin").password("password").roles("ADMIN").build(),
            User.withDefaultPasswordEncoder().username("pilot_user").password("pilot123").roles("READER").build(),
            User.withDefaultPasswordEncoder().username("crew_user").password("crew123").roles("READER").build()
        );
    }
}

@RestController
@RequestMapping("/api/roster")
@CrossOrigin(origins = "*")
class RosterController {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final RosterSqlRepository sqlRepo;
    private final RosterMongoRepository mongoRepo;

    private final String FLIGHT_API_BASE;
    private final String PILOT_API;
    private final String CREW_API;
    private final String PASSENGER_API;

    public RosterController(RestTemplate restTemplate, RosterSqlRepository sqlRepo, RosterMongoRepository mongoRepo) {
        this.restTemplate = restTemplate;
        this.sqlRepo = sqlRepo;
        this.mongoRepo = mongoRepo;
        this.objectMapper = new ObjectMapper();

        String flightHost = System.getenv("FLIGHT_HOST"); if (flightHost == null) flightHost = "flight-info-api";
        String pilotHost = System.getenv("PILOT_HOST"); if (pilotHost == null) pilotHost = "pilot-api";
        String crewHost = System.getenv("CREW_HOST"); if (crewHost == null) crewHost = "crew-api";
        String passengerHost = System.getenv("PASSENGER_HOST"); if (passengerHost == null) passengerHost = "passenger-api";

        this.FLIGHT_API_BASE = "http://" + flightHost + ":8081/api/flights";
        this.PILOT_API = "http://" + pilotHost + ":8082/api/pilots";
        this.CREW_API = "http://" + crewHost + ":8083/api/cabin-crew";
        this.PASSENGER_API = "http://" + passengerHost + ":8084/api/passengers";
    }

    // --- ID NORMALIZATION METHOD ---
    private String normalizeId(String id) {
        if (id == null) return null;
        // removing spaces and converting to uppercase just in case: "TK 1001" -> "TK1001"
        return id.replaceAll("\\s+", "").toUpperCase();
    }

    @GetMapping("/generate/{flightId}")
    public ResponseEntity<?> generateRoster(@PathVariable("flightId") String rawFlightId, 
                                          @RequestParam(value = "forceNew", required = false) boolean forceNew) {
        
        String flightId = normalizeId(rawFlightId);
        System.out.println("Processing Roster for Normalized ID: " + flightId + " (ForceNew: " + forceNew + ")");

        if (!forceNew) {
            try {
                // 1. Standard Search
                List<RosterSqlEntity> sqlList = sqlRepo.findByFlightIdOrderByGeneratedDateDesc(flightId);
                
                Map<String, Object> finalData = null;
                
                if (!sqlList.isEmpty()) {
                    System.out.println("Found in SQL directly.");
                    finalData = objectMapper.readValue(sqlList.get(0).getRosterData(), new TypeReference<Map<String, Object>>(){});
                } 
                
                // 2. Backup Search (Brute force scan if direct lookup fails)
                if (finalData == null) {
                    System.out.println("Direct match failed. Scanning all records...");
                    List<RosterSqlEntity> all = sqlRepo.findAll();
                    for (RosterSqlEntity ent : all) {
                        String dbId = normalizeId(ent.getFlightId());
                        if (dbId != null && dbId.equals(flightId)) {
                            System.out.println("Found in SQL via scan -> DB ID: " + ent.getFlightId());
                            finalData = objectMapper.readValue(ent.getRosterData(), new TypeReference<Map<String, Object>>(){});
                            break;
                        }
                    }
                }

                // 3. Mongo check
                if (finalData == null) {
                    List<RosterDocument> mongoList = mongoRepo.findByFlightIdOrderByGeneratedDateDesc(flightId);
                    if (!mongoList.isEmpty()) {
                        System.out.println("Found in Mongo.");
                        finalData = mongoList.get(0).getRosterData();
                    }
                }

                if (finalData != null) return ResponseEntity.ok(finalData);

            } catch (Exception e) { 
                System.err.println("DB Load Error: " + e.getMessage()); 
            }
        }

        System.out.println("No DB record found. Creating new from services...");

        // If no record found, create a new one
        Map<String, Object> response = new HashMap<>();
        try {
            Map flight = fetchWithFallback(FLIGHT_API_BASE + "/" + rawFlightId, "Flight");
            
            // If raw ID fails, try with normalized one
            if (flight == null && !rawFlightId.equals(flightId)) {
                 flight = fetchWithFallback(FLIGHT_API_BASE + "/" + flightId, "Flight");
            }

            if (flight == null) return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Collections.singletonMap("error", "Flight not found"));
            
            // Shared Info
            try {
                Map sharedInfo = restTemplate.getForObject(FLIGHT_API_BASE + "/" + flightId + "/shared-info", Map.class);
                if (sharedInfo != null) flight.put("sharedDetails", sharedInfo);
            } catch (Exception e) { }
            
            response.put("flightInfo", flight);
            Map vehicle = (Map) flight.get("vehicleType");
            String vehicleModel = ((String) vehicle.get("modelName"));
            int distance = (flight.get("distanceKm") instanceof Number) ? ((Number) flight.get("distanceKm")).intValue() : 0;

            // Pilots section
            List<Map> selectedPilots = new ArrayList<>();
            try {
                Map[] pilotArray = restTemplate.getForObject(PILOT_API, Map[].class);
                if (pilotArray != null) {
                    List<Map> suitable = Arrays.stream(pilotArray)
                        .filter(p -> {
                            String allowed = (String) p.get("allowedVehicleType");
                            int range = (p.get("allowedRangeKm") instanceof Number) ? ((Number) p.get("allowedRangeKm")).intValue() : 0;
                            boolean vehicleMatch = allowed != null && allowed.toLowerCase().contains(vehicleModel.toLowerCase());
                            return vehicleMatch && range >= distance;
                        }).collect(Collectors.toList());

                    Optional<Map> senior = suitable.stream().filter(p -> "SENIOR".equalsIgnoreCase((String)p.get("seniorityLevel"))).findFirst();
                    Optional<Map> junior = suitable.stream().filter(p -> "JUNIOR".equalsIgnoreCase((String)p.get("seniorityLevel"))).findFirst();
                    if (senior.isPresent() && junior.isPresent()) {
                        selectedPilots.add(senior.get());
                        selectedPilots.add(junior.get());
                    }
                }
            } catch (Exception e) { }
            response.put("pilots", selectedPilots);

            // Cabin Crew section
            List<Map> selectedCrew = new ArrayList<>();
            try {
                Map[] crewArray = restTemplate.getForObject(CREW_API, Map[].class);
                if (crewArray != null) {
                    List<Map> suitableCrew = Arrays.stream(crewArray)
                        .filter(c -> {
                            List<String> allowed = (List<String>) c.get("allowedVehicles");
                            return allowed != null && allowed.stream().anyMatch(v -> v.toLowerCase().contains(vehicleModel.toLowerCase()));
                        }).collect(Collectors.toList());

                    List<Map> chiefs = suitableCrew.stream().filter(c -> "CHIEF".equals(c.get("type"))).collect(Collectors.toList());
                    List<Map> chefs = suitableCrew.stream().filter(c -> "CHEF".equals(c.get("type"))).collect(Collectors.toList());
                    List<Map> others = suitableCrew.stream().filter(c -> "REGULAR".equals(c.get("type"))).collect(Collectors.toList());

                    if (!chiefs.isEmpty()) selectedCrew.add(chiefs.get(0));
                    if (!chefs.isEmpty()) selectedCrew.add(chefs.get(0));
                    selectedCrew.addAll(others.stream().limit(4).collect(Collectors.toList()));
                }
            } catch (Exception e) { }
            response.put("cabinCrew", selectedCrew);

            // Passenger list
            List<Map> allPassengers = new ArrayList<>();
            try {
                Map[] passArray = restTemplate.getForObject(PASSENGER_API + "/flight/" + rawFlightId, Map[].class); 
                if (passArray != null) {
                    allPassengers = new ArrayList<>(Arrays.asList(passArray));
                    assignMissingSeatsSmartly(allPassengers, vehicle);
                }
            } catch (Exception e) { }
            response.put("passengers", allPassengers);

            // Food menu
            List<String> flightMenu = new ArrayList<>();
            try {
                flightMenu.add((String) vehicle.get("standardMenuDescription"));
                for(Map crew : selectedCrew) {
                    if("CHEF".equals(crew.get("type"))) {
                        List<String> recipes = (List<String>) crew.get("chefRecipes");
                        if(recipes != null && !recipes.isEmpty()) flightMenu.add("Chef's Special: " + recipes.get(0));
                    }
                }
            } catch(Exception e) { flightMenu.add("Standard Menu"); }
            response.put("menu", flightMenu);
            
            response.put("flightId", flightId);
            response.put("generatedDate", new Date());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Collections.singletonMap("error", e.getMessage()));
        }
    }

    private void assignMissingSeatsSmartly(List<Map> passengers, Map vehicleInfo) {
        try {
            String configJson = (String) vehicleInfo.get("seatingPlanConfig");
            if(configJson == null) return;
            Map<String, Object> config = objectMapper.readValue(configJson, new TypeReference<Map<String, Object>>(){});
            List<Map<String, Object>> sections = (List<Map<String, Object>>) config.get("sections");
            Set<String> occupiedSeats = new HashSet<>();
            Map<Integer, Map> passengerById = new HashMap<>();
            List<Map> unseatedPassengers = new ArrayList<>();

            for(Map p : passengers) {
                Number idNum = (Number) p.get("id");
                if(idNum != null) passengerById.put(idNum.intValue(), p);
                String s = (String) p.get("seatNumber");
                Object ageObj = p.get("age");
                int age = (ageObj instanceof Number) ? ((Number) ageObj).intValue() : 18;
                if (age <= 2) { p.put("seatNumber", "INFANT (Lap)"); continue; }
                if(s != null && !s.isEmpty()) occupiedSeats.add(s);
                else unseatedPassengers.add(p);
            }

            List<String> availableEconomy = new ArrayList<>();
            List<String> availableBusiness = new ArrayList<>();
            int currentRow = 1;
            for(Map<String, Object> section : sections) {
                String className = ((String) section.get("className")).toUpperCase();
                int rows = (Integer) section.get("rows");
                String letters = (String) section.get("letters");
                List<String> targetList = "BUSINESS".equals(className) ? availableBusiness : availableEconomy;
                for(int r=0; r<rows; r++) {
                    for(char c : letters.toCharArray()) {
                        String seat = currentRow + String.valueOf(c);
                        if(!occupiedSeats.contains(seat)) targetList.add(seat);
                    }
                    currentRow++;
                }
            }

            Set<Integer> processedIds = new HashSet<>();
            for (Map p : unseatedPassengers) {
                Number pIdNum = (Number) p.get("id");
                if (pIdNum == null || processedIds.contains(pIdNum.intValue())) continue;
                List<Map> group = new ArrayList<>();
                group.add(p);
                processedIds.add(pIdNum.intValue());
                List<Number> affiliateIds = (List<Number>) p.get("affiliatedPassengerIds");
                if (affiliateIds != null) {
                    for (Number affId : affiliateIds) {
                        if (!processedIds.contains(affId.intValue()) && passengerById.containsKey(affId.intValue())) {
                            Map affiliate = passengerById.get(affId.intValue());
                            if (unseatedPassengers.contains(affiliate)) {
                                group.add(affiliate);
                                processedIds.add(affId.intValue());
                            }
                        }
                    }
                }
                String seatType = (String) p.get("seatType");
                List<String> pool = "BUSINESS".equalsIgnoreCase(seatType) ? availableBusiness : availableEconomy;
                for (Map member : group) {
                    if (!pool.isEmpty()) {
                        member.put("seatNumber", pool.remove(0));
                        member.put("autoAssigned", true);
                    } else member.put("seatNumber", "STANDBY");
                }
            }
        } catch (Exception e) { e.printStackTrace(); }
    }

    private Map fetchWithFallback(String url, String serviceName) {
        try { return restTemplate.getForObject(url, Map.class); } catch (Exception e) {
            try {
                String localUrl = url.replace("flight-info-api", "localhost").replace("pilot-api", "localhost").replace("crew-api", "localhost").replace("passenger-api", "localhost");
                return restTemplate.getForObject(localUrl, Map.class);
            } catch (Exception ex) { return null; }
        }
    }
    
    // --- SAVE METHOD (ID NORMALIZATION INCLUDED) ---
    @PostMapping("/save")
    public ResponseEntity<?> saveRoster(@RequestBody Map<String, Object> rosterData, @RequestParam(name="dbType", defaultValue="sql") String dbType) {
        try {
            validateRosterRules(rosterData);
            
            String rawId = (String) rosterData.get("flightId");
            if(rawId == null) {
                Map info = (Map) rosterData.get("flightInfo");
                if(info != null) rawId = (String) info.get("flightNumber");
            }
            if(rawId == null) throw new IllegalArgumentException("Flight ID is missing");
            
            // STRICT ID CLEANING
            String flightId = normalizeId(rawId);
            
            // Putting the corrected ID back into data
            rosterData.put("flightId", flightId);
            Map info = (Map) rosterData.get("flightInfo");
            if(info != null) info.put("flightNumber", flightId);

            Date now = new Date();
            rosterData.put("generatedDate", now);

            System.out.println("Saving Roster: " + flightId + " (Raw was: " + rawId + ")");

            if ("mongo".equalsIgnoreCase(dbType)) {
                List<RosterDocument> existingDocs = mongoRepo.findByFlightIdOrderByGeneratedDateDesc(flightId);
                RosterDocument doc = !existingDocs.isEmpty() ? existingDocs.get(0) : new RosterDocument();
                if(doc.getId() == null) doc.setFlightId(flightId);
                doc.setGeneratedDate(now);
                doc.setRosterData(rosterData);
                mongoRepo.save(doc);
            } else {
                List<RosterSqlEntity> existingEntities = sqlRepo.findByFlightIdOrderByGeneratedDateDesc(flightId);
                RosterSqlEntity entity = !existingEntities.isEmpty() ? existingEntities.get(0) : new RosterSqlEntity();
                if(entity.getId() == null) entity.setFlightId(flightId);
                entity.setGeneratedDate(now);
                entity.setRosterData(objectMapper.writeValueAsString(rosterData));
                sqlRepo.save(entity);
            }
            return ResponseEntity.ok("Saved to " + dbType);
        } catch (Exception e) { 
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Save failed: " + e.getMessage()); 
        }
    }
    
    @GetMapping("/flights")
    public List<Map<String, Object>> getFlights() {
        List<Map<String, Object>> combinedFlights = new ArrayList<>();
        Set<String> existingFlightNumbers = new HashSet<>();

        try {
            Map[] standardFlights = restTemplate.getForObject(FLIGHT_API_BASE, Map[].class);
            if (standardFlights != null) {
                for (Map f : standardFlights) {
                    combinedFlights.add(f);
                    String fNum = (String) f.get("flightNumber");
                    if(fNum != null) existingFlightNumbers.add(normalizeId(fNum));
                }
            }
        } catch (Exception e) { System.err.println("Flight API Error: " + e.getMessage()); }

        List<RosterSqlEntity> savedRosters = sqlRepo.findAll();
        for (RosterSqlEntity roster : savedRosters) {
            try {
                Map<String, Object> rosterData = objectMapper.readValue(roster.getRosterData(), new TypeReference<Map<String, Object>>(){});
                Map<String, Object> flightInfo = (Map<String, Object>) rosterData.get("flightInfo");

                if (flightInfo != null) {
                    String fNumRaw = (String) flightInfo.get("flightNumber");
                    String fNumNorm = normalizeId(fNumRaw);
                    
                    if (fNumNorm != null && !existingFlightNumbers.contains(fNumNorm)) {
                        combinedFlights.add(flightInfo);
                        existingFlightNumbers.add(fNumNorm);
                    }
                }
            } catch (Exception e) { }
        }
        return combinedFlights;
    }
    
    private void validateRosterRules(Map<String, Object> rosterData) {
        List<Map> pilots = (List<Map>) rosterData.get("pilots");
        if (pilots == null || pilots.size() < 2) throw new IllegalArgumentException("Minimum 2 pilots required.");
    }

    @GetMapping("/candidates/pilots/{vehicleType}")
    public List<Map> getCandidatePilots(@PathVariable("vehicleType") String vehicleType, @RequestParam(value = "date", required = false) String dateStr, @RequestParam(value = "currentFlightId", required = false) String currentFlightId) {
        try {
             Map[] allPilots = restTemplate.getForObject(PILOT_API, Map[].class);
             return Arrays.asList(allPilots); 
        } catch(Exception e) { return new ArrayList<>(); }
    }
    @GetMapping("/candidates/crew")
    public List<Map> getCandidateCrew(@RequestParam(value = "date", required = false) String dateStr, @RequestParam(value = "currentFlightId", required = false) String currentFlightId) {
        try {
             Map[] allCrew = restTemplate.getForObject(CREW_API, Map[].class);
             return Arrays.asList(allCrew);
        } catch(Exception e) { return new ArrayList<>(); }
    }
}

// ENTITIES
@Entity @Table(name = "rosters")
class RosterSqlEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    private String flightId;
    @Temporal(TemporalType.TIMESTAMP) private Date generatedDate;
    @Lob @Column(columnDefinition = "TEXT") private String rosterData;
    public Long getId() { return id; } public void setId(Long id) { this.id = id; }
    public String getFlightId() { return flightId; } public void setFlightId(String f) { this.flightId = f; }
    public Date getGeneratedDate() { return generatedDate; } public void setGeneratedDate(Date d) { this.generatedDate = d; }
    public String getRosterData() { return rosterData; } public void setRosterData(String r) { this.rosterData = r; }
}
interface RosterSqlRepository extends JpaRepository<RosterSqlEntity, Long> {
    List<RosterSqlEntity> findByFlightIdOrderByGeneratedDateDesc(String flightId);
}
@Document(collection = "rosters")
class RosterDocument {
    @org.springframework.data.annotation.Id private String id;
    private String flightId;
    private Date generatedDate;
    private Map<String, Object> rosterData;
    public String getId() { return id; } public void setId(String id) { this.id = id; }
    public String getFlightId() { return flightId; } public void setFlightId(String f) { this.flightId = f; }
    public Date getGeneratedDate() { return generatedDate; } public void setGeneratedDate(Date d) { this.generatedDate = d; }
    public Map<String, Object> getRosterData() { return rosterData; } public void setRosterData(Map<String, Object> r) { this.rosterData = r; }
}
interface RosterMongoRepository extends MongoRepository<RosterDocument, String> {
    List<RosterDocument> findByFlightIdOrderByGeneratedDateDesc(String flightId);
}