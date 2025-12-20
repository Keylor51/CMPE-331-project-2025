package com.cmpe331.passenger;

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

import java.util.List;
import java.util.Set;
import java.util.Random;

import static org.springframework.security.config.Customizer.withDefaults;

@SpringBootApplication
public class PassengerApi {
    public static void main(String[] args) {
        System.setProperty("server.port", "8084");
        System.setProperty("spring.datasource.url", "jdbc:sqlite:passenger_db.sqlite");
        SpringApplication.run(PassengerApi.class, args);
    }

    @Bean
    CommandLineRunner initPassengers(PassengerRepository passengerRepo) {
        return args -> {
            if (passengerRepo.count() == 0) {
                // ==========================================
                // 1. SPECIAL SCENARIO PASSENGERS (Existing)
                // ==========================================
                
                // --- TK1001 (ISTANBUL -> LONDON) ---
                Passenger p1 = new Passenger("James Bond", 40, "Male", "UK", "TK1001", "BUSINESS", "1A");
                passengerRepo.save(p1);

                Passenger mother = new Passenger("Fatma Kaya", 28, "Female", "Turkey", "TK1001", "ECONOMY", "5A");
                mother = passengerRepo.save(mother);
                
                Passenger baby = new Passenger("Can Kaya", 1, "Male", "Turkey", "TK1001", "ECONOMY", "INFANT (Lap)");
                baby.setParentId(mother.getId());
                passengerRepo.save(baby);

                Passenger husband = new Passenger("Ali Demir", 30, "Male", "Turkey", "TK1001", "ECONOMY", null);
                husband = passengerRepo.save(husband);
                
                Passenger wife = new Passenger("Ayse Demir", 29, "Female", "Turkey", "TK1001", "ECONOMY", null);
                wife = passengerRepo.save(wife);

                husband.setAffiliatedPassengerIds(Set.of(wife.getId()));
                wife.setAffiliatedPassengerIds(Set.of(husband.getId()));
                passengerRepo.save(husband);
                passengerRepo.save(wife);

                // --- TK2020 (ISTANBUL -> NEW YORK) ---
                passengerRepo.save(new Passenger("Elon Musk", 50, "Male", "USA", "TK2020", "BUSINESS", "1A"));
                
                // --- TK3030 (ISTANBUL -> TOKYO) ---
                passengerRepo.save(new Passenger("Naruto Uzumaki", 20, "Male", "Japan", "TK3030", "ECONOMY", "15F"));

                // ==========================================
                // 2. ADDING CROWDED PASSENGERS (New)
                // ==========================================
                System.out.println("Generating random passengers to fill flights...");

                // TK1001 (Embraer E195 - Regional)
                // Business: 1-3 row (1+2 layout: A - D F), Economy: 5-16 row (2+2 layout: A C - D F)
                fillFlight(passengerRepo, "TK1001", "BUSINESS", 1, 3, new String[]{"D", "F"}); // 1A taken
                fillFlight(passengerRepo, "TK1001", "ECONOMY", 6, 12, new String[]{"A", "C", "D", "F"});

                // TK2020 (Boeing 737-800 - Narrow)
                // Business: 1-3 row (2+2), Economy: 5-29 row (3+3)
                fillFlight(passengerRepo, "TK2020", "BUSINESS", 1, 3, new String[]{"C", "D", "F"}); // 1A taken
                fillFlight(passengerRepo, "TK2020", "ECONOMY", 6, 20, new String[]{"A", "B", "C", "D", "E", "F"});

                // TK3030 (Boeing 787 - Wide)
                // Business: 1-5 row (2+2+2), Economy: 6-40 row (3+4+3)
                fillFlight(passengerRepo, "TK3030", "BUSINESS", 2, 5, new String[]{"A", "C", "D", "G", "H", "K"});
                fillFlight(passengerRepo, "TK3030", "ECONOMY", 10, 35, new String[]{"A", "B", "C", "D", "E", "F", "G", "H", "J", "K"});

                System.out.println("--- PASSENGER DB SEEDED WITH MASSIVE CROWD ---");
            }
        };
    }

    private void fillFlight(PassengerRepository repo, String flightId, String type, int startRow, int endRow, String[] letters) {
        String[] names = {"Ahmet", "Mehmet", "Ayse", "Fatma", "Mustafa", "Zeynep", "Emre", "Selin", "John", "Jane", "Michael", "Sarah", "David", "Emma", "Robert", "Olivia", "Hans", "Helga", "Yuki", "Hiro"};
        String[] surnames = {"Yilmaz", "Kaya", "Demir", "Celik", "Sahin", "Ozturk", "Smith", "Johnson", "Brown", "Taylor", "Miller", "Wilson", "Muller", "Weber", "Sato", "Tanaka"};
        String[] nations = {"Turkey", "USA", "UK", "Germany", "Japan", "France", "Italy", "Spain"};
        
        Random rand = new Random();

        for (int r = startRow; r <= endRow; r++) {
            for (String l : letters) {
                // Let's have 80% occupancy, not every seat should be full
                if (rand.nextDouble() > 0.80) continue;

                String name = names[rand.nextInt(names.length)] + " " + surnames[rand.nextInt(surnames.length)];
                int age = 18 + rand.nextInt(60);
                String gender = rand.nextBoolean() ? "Male" : "Female";
                String nat = nations[rand.nextInt(nations.length)];
                String seat = r + l;

                Passenger p = new Passenger(name, age, gender, nat, flightId, type, seat);
                repo.save(p);
            }
        }
    }
}

@Configuration @EnableWebSecurity
class SecurityConfig {
    @Bean public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.csrf(c -> c.disable()).authorizeHttpRequests(a -> a.anyRequest().permitAll()).httpBasic(withDefaults()); return http.build();
    }
    @Bean public InMemoryUserDetailsManager userDetailsService() {
        return new InMemoryUserDetailsManager(User.withDefaultPasswordEncoder().username("admin").password("password").roles("USER").build());
    }
}

@RestController @RequestMapping("/api/passengers") @CrossOrigin(origins = "*")
class PassengerController {
    private final PassengerRepository repo;
    public PassengerController(PassengerRepository repo) { this.repo = repo; }
    @GetMapping public List<Passenger> getAll() { return repo.findAll(); }
    @GetMapping("/flight/{flightId}") public ResponseEntity<List<Passenger>> getByFlight(@PathVariable("flightId") String flightId) { return ResponseEntity.ok(repo.findByFlightId(flightId)); }
}

interface PassengerRepository extends JpaRepository<Passenger, Long> { List<Passenger> findByFlightId(String flightId); }

@Entity @Table(name = "passengers")
class Passenger {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    private String name; private int age; private String gender; private String nationality;
    private String flightId; private String seatType; private String seatNumber; private Long parentId;
    @ElementCollection(fetch = FetchType.EAGER) @CollectionTable(name = "passenger_affiliations", joinColumns = @JoinColumn(name = "passenger_id")) @Column(name = "affiliated_id") private Set<Long> affiliatedPassengerIds;
    public Passenger() {}
    public Passenger(String name, int age, String gender, String nationality, String flightId, String seatType, String seatNumber) {
        this.name = name; this.age = age; this.gender = gender; this.nationality = nationality; this.flightId = flightId; this.seatType = seatType; this.seatNumber = seatNumber;
    }
    public Long getId() { return id; } public String getName() { return name; } public int getAge() { return age; } public String getGender() { return gender; } public String getNationality() { return nationality; } public String getFlightId() { return flightId; } public String getSeatType() { return seatType; } public String getSeatNumber() { return seatNumber; } public void setSeatNumber(String seatNumber) { this.seatNumber = seatNumber; } public Long getParentId() { return parentId; } public void setParentId(Long parentId) { this.parentId = parentId; } public Set<Long> getAffiliatedPassengerIds() { return affiliatedPassengerIds; } public void setAffiliatedPassengerIds(Set<Long> affiliatedPassengerIds) { this.affiliatedPassengerIds = affiliatedPassengerIds; }
}