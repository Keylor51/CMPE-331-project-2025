package com.cmpe331.passenger;

import jakarta.persistence.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

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
                Passenger p1 = new Passenger("Elon Musk", 50, "Male", "USA", "TK1001", "BUSINESS");
                p1.setSeatNumber("1A");
                p1 = passengerRepo.save(p1);

                Passenger mom = new Passenger("Mary Doe", 30, "Female", "UK", "TK1001", "ECONOMY");
                mom = passengerRepo.save(mom);

                Passenger child = new Passenger("Jimmy Doe", 8, "Male", "UK", "TK1001", "ECONOMY");
                child.setAffiliatedPassengerIds(Set.of(mom.getId()));
                child = passengerRepo.save(child);

                mom.setAffiliatedPassengerIds(Set.of(child.getId()));
                passengerRepo.save(mom);

                Passenger infant = new Passenger("Baby Doe", 1, "Female", "UK", "TK1001", "ECONOMY");
                infant.setParentId(mom.getId());
                passengerRepo.save(infant);
                System.out.println("--- PASSENGER DB SEEDED ---");
            }
        };
    }
}

// --- SECURITY CONFIG ---
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
@RequestMapping("/api/passengers")
@CrossOrigin(origins = "*")
class PassengerController {
    private final PassengerRepository passengerRepo;
    public PassengerController(PassengerRepository passengerRepo) { this.passengerRepo = passengerRepo; }

    @GetMapping
    public List<Passenger> getAll() { return passengerRepo.findAll(); }

    @GetMapping("/flight/{flightId}")
    public List<Passenger> getByFlight(@PathVariable("flightId") String flightId) {
        return passengerRepo.findByFlightId(flightId);
    }
}

interface PassengerRepository extends JpaRepository<Passenger, Long> {
    List<Passenger> findByFlightId(String flightId);
}

@Entity @Table(name = "passengers")
class Passenger {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    private String name; private int age; private String gender; private String nationality;
    private String flightId; private String seatType; private String seatNumber; private Long parentId;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "passenger_affiliations", joinColumns = @JoinColumn(name = "passenger_id"))
    @Column(name = "affiliated_id") private Set<Long> affiliatedPassengerIds;

    public Passenger() {}
    public Passenger(String name, int age, String gender, String nationality, String flightId, String seatType) {
        this.name = name; this.age = age; this.gender = gender; this.nationality = nationality; this.flightId = flightId; this.seatType = seatType;
    }
    // Getters/Setters
    public Long getId() { return id; }
    public String getName() { return name; }
    public int getAge() { return age; }
    public String getGender() { return gender; }
    public String getNationality() { return nationality; }
    public String getFlightId() { return flightId; }
    public String getSeatType() { return seatType; }
    public String getSeatNumber() { return seatNumber; }
    public void setSeatNumber(String seatNumber) { this.seatNumber = seatNumber; }
    public Long getParentId() { return parentId; }
    public void setParentId(Long parentId) { this.parentId = parentId; }
    public Set<Long> getAffiliatedPassengerIds() { return affiliatedPassengerIds; }
    public void setAffiliatedPassengerIds(Set<Long> affiliatedPassengerIds) { this.affiliatedPassengerIds = affiliatedPassengerIds; }
}