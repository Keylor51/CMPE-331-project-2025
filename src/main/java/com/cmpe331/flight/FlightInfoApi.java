package com.cmpe331.flight;

import jakarta.persistence.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

import static org.springframework.security.config.Customizer.withDefaults;

@SpringBootApplication
public class FlightInfoApi {
    public static void main(String[] args) {
        System.setProperty("server.port", "8081");
        System.setProperty("spring.datasource.url", "jdbc:sqlite:flight_db.sqlite");
        SpringApplication.run(FlightInfoApi.class, args);
    }
    @Bean
    CommandLineRunner initDatabase(AirportRepository airportRepo, VehicleTypeRepository vehicleRepo, FlightRepository flightRepo, SharedFlightRepository sharedRepo) {
        return args -> {
             if (airportRepo.count() == 0) {
                Airport ist = new Airport("IST", "Istanbul Airport", "Istanbul", "Turkey");
                Airport lhr = new Airport("LHR", "Heathrow Airport", "London", "UK");
                Airport jfk = new Airport("JFK", "John F. Kennedy", "New York", "USA");
                Airport nrt = new Airport("NRT", "Narita Airport", "Tokyo", "Japan");
                airportRepo.saveAll(List.of(ist, lhr, jfk, nrt));

                String configRegional = "{\"sections\": [{\"className\": \"BUSINESS\", \"rows\": 3, \"layout\": [1, 2], \"letters\": \"ADF\"},{\"className\": \"ECONOMY\", \"rows\": 12, \"layout\": [2, 2], \"letters\": \"ACDF\"}]}";
                VehicleType vRegional = new VehicleType("Embraer E195", 76, 4, 76, configRegional, "Sandwich & Juice");
                String configNarrow = "{\"sections\": [{\"className\": \"BUSINESS\", \"rows\": 3, \"layout\": [2, 2], \"letters\": \"ACDF\"},{\"className\": \"ECONOMY\", \"rows\": 25, \"layout\": [3, 3], \"letters\": \"ABCDEF\"}]}";
                VehicleType vNarrow = new VehicleType("Boeing 737-800", 162, 6, 162, configNarrow, "Hot Meal (Chicken/Pasta)");
                String configWide = "{\"sections\": [{\"className\": \"BUSINESS\", \"rows\": 5, \"layout\": [2, 2, 2], \"letters\": \"ACDGHK\"},{\"className\": \"ECONOMY\", \"rows\": 35, \"layout\": [3, 4, 3], \"letters\": \"ABCDEFGHJK\"}]}";
                VehicleType vWide = new VehicleType("Boeing 787 Dreamliner", 350, 12, 350, configWide, "International Multi-Course");
                vehicleRepo.saveAll(List.of(vRegional, vNarrow, vWide));

                Flight f1 = new Flight("TK1001", LocalDateTime.now().plusDays(1), 120, 1500, ist, lhr, vRegional);
                Flight f2 = new Flight("TK2020", LocalDateTime.now().plusDays(2), 600, 8000, ist, jfk, vNarrow);
                Flight f3 = new Flight("TK3030", LocalDateTime.now().plusDays(3), 660, 9000, ist, nrt, vWide);
                flightRepo.saveAll(List.of(f1, f2, f3));
             }
        };
    }
}
// Security, Controller, Entities 
@Configuration @EnableWebSecurity
class SecurityConfig {
    @Bean public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.csrf(c -> c.disable()).authorizeHttpRequests(a -> a.anyRequest().authenticated()).httpBasic(withDefaults()); return http.build();
    }
    @Bean public InMemoryUserDetailsManager userDetailsService() {
        return new InMemoryUserDetailsManager(User.withDefaultPasswordEncoder().username("admin").password("password").roles("USER").build());
    }
}
@RestController @RequestMapping("/api/flights") @CrossOrigin(origins = "*") 
class FlightController {
    private final FlightRepository flightRepo;
    private final SharedFlightRepository sharedRepo;
    public FlightController(FlightRepository f, SharedFlightRepository s) { this.flightRepo = f; this.sharedRepo = s; }
    @GetMapping public List<Flight> getAllFlights() { return flightRepo.findAll(); }
    @GetMapping("/{flightNumber}") public ResponseEntity<Flight> getFlight(@PathVariable("flightNumber") String flightNumber) {
        return flightRepo.findAll().stream().filter(f -> f.getFlightNumber().equals(flightNumber)).findFirst().map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }
    @GetMapping("/{flightNumber}/shared-info") public ResponseEntity<SharedFlightDetails> getSharedInfo(@PathVariable("flightNumber") String flightNumber) { return sharedRepo.findById(flightNumber).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build()); }
}
interface FlightRepository extends JpaRepository<Flight, String> {}
interface AirportRepository extends JpaRepository<Airport, String> {}
interface VehicleTypeRepository extends JpaRepository<VehicleType, Integer> {}
interface SharedFlightRepository extends JpaRepository<SharedFlightDetails, String> {}
@Entity @Table(name = "airports")
class Airport {
    @Id @Column(length = 3) private String code;
    private String name; private String city; private String country;
    public Airport() {}
    public Airport(String c, String n, String ci, String co) { code=c; name=n; city=ci; country=co; }
    public String getCode() { return code; } public String getName() { return name; } public String getCity() { return city; } public String getCountry() { return country; }
}
@Entity @Table(name = "vehicle_types")
class VehicleType {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Integer id;
    private String modelName; private int totalSeats; private int crewLimit; private int passengerLimit;
    @Column(length = 4000) private String seatingPlanConfig;
    private String standardMenuDescription;
    public VehicleType() {}
    public VehicleType(String m, int t, int c, int p, String s, String menu) { modelName=m; totalSeats=t; crewLimit=c; passengerLimit=p; seatingPlanConfig=s; standardMenuDescription=menu; }
    public Integer getId() { return id; } public String getModelName() { return modelName; } public int getTotalSeats() { return totalSeats; } public String getSeatingPlanConfig() { return seatingPlanConfig; } public String getStandardMenuDescription() { return standardMenuDescription; }
}
@Entity @Table(name = "flights")
class Flight {
    @Id @Column(length = 6) private String flightNumber;
    private LocalDateTime dateTime; private int durationMinutes; private int distanceKm;
    @ManyToOne @JoinColumn(name = "source_airport_code") private Airport source;
    @ManyToOne @JoinColumn(name = "destination_airport_code") private Airport destination;
    @ManyToOne @JoinColumn(name = "vehicle_type_id") private VehicleType vehicleType;
    public Flight() {}
    public Flight(String f, LocalDateTime d, int dum, int dis, Airport s, Airport des, VehicleType v) { flightNumber=f; dateTime=d; durationMinutes=dum; distanceKm=dis; source=s; destination=des; vehicleType=v; }
    public String getFlightNumber() { return flightNumber; } public LocalDateTime getDateTime() { return dateTime; } public Airport getSource() { return source; } public Airport getDestination() { return destination; } public VehicleType getVehicleType() { return vehicleType; } public int getDistanceKm() { return distanceKm; }
}
@Entity @Table(name = "shared_flight_details")
class SharedFlightDetails {
    @Id private String localFlightNumber;
    private String partnerCompanyName; private String partnerFlightNumber; private String connectingFlightInfo;
    public SharedFlightDetails() {}
    public SharedFlightDetails(String l, String p, String pf, String c) { localFlightNumber=l; partnerCompanyName=p; partnerFlightNumber=pf; connectingFlightInfo=c; }
    public String getLocalFlightNumber() { return localFlightNumber; } public String getPartnerCompanyName() { return partnerCompanyName; } public String getPartnerFlightNumber() { return partnerFlightNumber; } public String getConnectingFlightInfo() { return connectingFlightInfo; }
}