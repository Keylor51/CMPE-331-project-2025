package com.cmpe331.flight;

import jakarta.persistence.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@SpringBootApplication
public class FlightInfoApi {

    public static void main(String[] args) {
        SpringApplication.run(FlightInfoApi.class, args);
    }

    /**
     * DATA SEEDER
     */
    @Bean
    CommandLineRunner initDatabase(AirportRepository airportRepo, 
                                   VehicleTypeRepository vehicleRepo, 
                                   FlightRepository flightRepo,
                                   SharedFlightRepository sharedRepo) {
        return args -> {
            if (airportRepo.count() == 0) {
                // Seed Airports
                Airport ist = new Airport("IST", "Istanbul Airport", "Istanbul", "Turkey");
                Airport lhr = new Airport("LHR", "Heathrow Airport", "London", "UK");
                Airport jfk = new Airport("JFK", "John F. Kennedy", "New York", "USA");
                airportRepo.saveAll(List.of(ist, lhr, jfk));

                // Seed Vehicle Types
                VehicleType v1 = new VehicleType("Boeing 737", 180, 6, 174, "{\"rows\":30, \"layout\":\"3-3\"}", "Standard Chicken or Pasta");
                VehicleType v2 = new VehicleType("Airbus A320", 150, 5, 145, "{\"rows\":25, \"layout\":\"3-3\"}", "Sandwich and Juice");
                vehicleRepo.saveAll(List.of(v1, v2));

                // Seed Flights
                Flight f1 = new Flight("TK1001", LocalDateTime.now().plusDays(1), 240, 2500, ist, lhr, v1);
                Flight f2 = new Flight("TK2020", LocalDateTime.now().plusDays(2), 600, 8000, ist, jfk, v1);
                flightRepo.saveAll(List.of(f1, f2));

                // Seed Shared Flight
                SharedFlightDetails shared = new SharedFlightDetails("TK2020", "United Airlines", "UA9090", "Connects via FRA");
                sharedRepo.save(shared);
                
                System.out.println("--- DATABASE SEEDED WITH DUMMY DATA ---");
            }
        };
    }
}

// ------------------- CONTROLLERS (API Endpoints) -------------------

@RestController
@RequestMapping("/api/flights")
@CrossOrigin(origins = "*") 
class FlightController {

    private final FlightRepository flightRepo;
    private final SharedFlightRepository sharedRepo;

    public FlightController(FlightRepository flightRepo, SharedFlightRepository sharedRepo) {
        this.flightRepo = flightRepo;
        this.sharedRepo = sharedRepo;
    }

    @GetMapping
    public List<Flight> getAllFlights() {
        return flightRepo.findAll();
    }

    // UPDATED: Added ("flightNumber") to @PathVariable
    @GetMapping("/{flightNumber}")
    public ResponseEntity<Flight> getFlight(@PathVariable("flightNumber") String flightNumber) {
        // Debug print to confirm code is updated
        System.out.println("DEBUG: Request received for flight: " + flightNumber);
        
        return flightRepo.findAll().stream()
                .filter(f -> f.getFlightNumber().equals(flightNumber))
                .findFirst()
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // UPDATED: Added ("flightNumber") to @PathVariable
    @GetMapping("/{flightNumber}/shared-info")
    public ResponseEntity<SharedFlightDetails> getSharedInfo(@PathVariable("flightNumber") String flightNumber) {
        return sharedRepo.findById(flightNumber)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
    
    // UPDATED: Added names to @RequestParam
    @GetMapping("/search")
    public List<Flight> searchFlights(@RequestParam("from") String from, @RequestParam("to") String to) {
        return flightRepo.findByRoute(from, to);
    }
}

// ------------------- REPOSITORIES -------------------

interface FlightRepository extends JpaRepository<Flight, String> {
    @Query("SELECT f FROM Flight f WHERE f.source.code = :fromCode AND f.destination.code = :toCode")
    List<Flight> findByRoute(String fromCode, String toCode);
}
interface AirportRepository extends JpaRepository<Airport, String> {}
interface VehicleTypeRepository extends JpaRepository<VehicleType, Integer> {}
interface SharedFlightRepository extends JpaRepository<SharedFlightDetails, String> {}

// ------------------- ENTITIES -------------------

@Entity
@Table(name = "airports")
class Airport {
    @Id
    @Column(length = 3)
    private String code;
    private String name;
    private String city;
    private String country;

    public Airport() {}
    public Airport(String code, String name, String city, String country) {
        this.code = code;
        this.name = name;
        this.city = city;
        this.country = country;
    }
    public String getCode() { return code; }
    public String getName() { return name; }
    public String getCity() { return city; }
    public String getCountry() { return country; }
}

@Entity
@Table(name = "vehicle_types")
class VehicleType {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    private String modelName;
    private int totalSeats;
    private int crewLimit;
    private int passengerLimit;
    private String seatingPlanConfig; 
    private String standardMenuDescription;

    public VehicleType() {}
    public VehicleType(String modelName, int totalSeats, int crewLimit, int passengerLimit, String seatingPlanConfig, String standardMenuDescription) {
        this.modelName = modelName;
        this.totalSeats = totalSeats;
        this.crewLimit = crewLimit;
        this.passengerLimit = passengerLimit;
        this.seatingPlanConfig = seatingPlanConfig;
        this.standardMenuDescription = standardMenuDescription;
    }
    public Integer getId() { return id; }
    public String getModelName() { return modelName; }
    public int getTotalSeats() { return totalSeats; }
    public String getSeatingPlanConfig() { return seatingPlanConfig; }
    public String getStandardMenuDescription() { return standardMenuDescription; }
}

@Entity
@Table(name = "flights")
class Flight {
    @Id
    @Column(length = 6)
    private String flightNumber;
    private LocalDateTime dateTime;
    private int durationMinutes;
    private int distanceKm;

    @ManyToOne
    @JoinColumn(name = "source_airport_code")
    private Airport source;

    @ManyToOne
    @JoinColumn(name = "destination_airport_code")
    private Airport destination;

    @ManyToOne
    @JoinColumn(name = "vehicle_type_id")
    private VehicleType vehicleType;

    public Flight() {}
    public Flight(String flightNumber, LocalDateTime dateTime, int durationMinutes, int distanceKm, Airport source, Airport destination, VehicleType vehicleType) {
        this.flightNumber = flightNumber;
        this.dateTime = dateTime;
        this.durationMinutes = durationMinutes;
        this.distanceKm = distanceKm;
        this.source = source;
        this.destination = destination;
        this.vehicleType = vehicleType;
    }
    public String getFlightNumber() { return flightNumber; }
    public LocalDateTime getDateTime() { return dateTime; }
    public Airport getSource() { return source; }
    public Airport getDestination() { return destination; }
    public VehicleType getVehicleType() { return vehicleType; }
    public int getDistanceKm() { return distanceKm; }
}

@Entity
@Table(name = "shared_flight_details")
class SharedFlightDetails {
    @Id
    private String localFlightNumber;
    private String partnerCompanyName;
    private String partnerFlightNumber;
    private String connectingFlightInfo;

    public SharedFlightDetails() {}
    public SharedFlightDetails(String localFlightNumber, String partnerCompanyName, String partnerFlightNumber, String connectingFlightInfo) {
        this.localFlightNumber = localFlightNumber;
        this.partnerCompanyName = partnerCompanyName;
        this.partnerFlightNumber = partnerFlightNumber;
        this.connectingFlightInfo = connectingFlightInfo;
    }
    public String getLocalFlightNumber() { return localFlightNumber; }
    public String getPartnerCompanyName() { return partnerCompanyName; }
    public String getPartnerFlightNumber() { return partnerFlightNumber; }
    public String getConnectingFlightInfo() { return connectingFlightInfo; }
}