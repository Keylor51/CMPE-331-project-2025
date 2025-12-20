package com.cmpe331.pilot;

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
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

import static org.springframework.security.config.Customizer.withDefaults;

@SpringBootApplication
public class PilotApi {
    public static void main(String[] args) {
        System.setProperty("server.port", "8082");
        System.setProperty("spring.datasource.url", "jdbc:sqlite:pilot_db.sqlite");
        SpringApplication.run(PilotApi.class, args);
    }

    @Bean
    CommandLineRunner initPilots(PilotRepository pilotRepo) {
        return args -> {
            if (pilotRepo.count() == 0) {
                // ==========================================
                // BOEING 787 DREAMLINER (Long Range - 15,000km+)
                // ==========================================
                pilotRepo.save(createPilot("Cpt. John Smith", 55, "Male", "USA", 18000, "Boeing 787 Dreamliner", "SENIOR", Set.of("English")));
                pilotRepo.save(createPilot("Cpt. Kenji Sato", 50, "Male", "Japan", 17000, "Boeing 787 Dreamliner", "SENIOR", Set.of("Japanese", "English")));
                pilotRepo.save(createPilot("Cpt. Maria Garcia", 48, "Female", "Spain", 16500, "Boeing 787 Dreamliner", "SENIOR", Set.of("Spanish", "English")));
                pilotRepo.save(createPilot("Cpt. Wei Chen", 52, "Male", "China", 18000, "Boeing 787 Dreamliner", "SENIOR", Set.of("Chinese", "English")));
                
                pilotRepo.save(createPilot("F.O. Sarah Connor", 32, "Female", "USA", 16000, "Boeing 787 Dreamliner", "JUNIOR", Set.of("English", "Spanish")));
                pilotRepo.save(createPilot("F.O. Ali Yilmaz", 30, "Male", "Turkey", 15000, "Boeing 787 Dreamliner", "JUNIOR", Set.of("Turkish", "English")));
                pilotRepo.save(createPilot("F.O. Pierre Dubois", 31, "Male", "France", 15500, "Boeing 787 Dreamliner", "JUNIOR", Set.of("French", "English")));
                pilotRepo.save(createPilot("F.O. Emma Watson", 29, "Female", "UK", 15000, "Boeing 787 Dreamliner", "JUNIOR", Set.of("English")));

                pilotRepo.save(createPilot("Tr. Mike Ross", 24, "Male", "USA", 10000, "Boeing 787 Dreamliner", "TRAINEE", Set.of("English")));
                pilotRepo.save(createPilot("Tr. Hans Zimmer", 23, "Male", "Germany", 10000, "Boeing 787 Dreamliner", "TRAINEE", Set.of("German", "English")));

                // ==========================================
                // BOEING 737-800 (Medium Range - 6,000km+)
                // ==========================================
                pilotRepo.save(createPilot("Cpt. Ahmet Demir", 48, "Male", "Turkey", 12000, "Boeing 737-800", "SENIOR", Set.of("Turkish", "English")));
                pilotRepo.save(createPilot("Cpt. Elif Kaya", 45, "Female", "Turkey", 11000, "Boeing 737-800", "SENIOR", Set.of("Turkish", "English", "German")));
                pilotRepo.save(createPilot("Cpt. Bruce Wayne", 42, "Male", "USA", 10000, "Boeing 737-800", "SENIOR", Set.of("English")));
                pilotRepo.save(createPilot("Cpt. Clark Kent", 39, "Male", "USA", 10000, "Boeing 737-800", "SENIOR", Set.of("English")));

                pilotRepo.save(createPilot("F.O. Mehmet Celik", 29, "Male", "Turkey", 8000, "Boeing 737-800", "JUNIOR", Set.of("Turkish", "English")));
                pilotRepo.save(createPilot("F.O. Ayse Yildiz", 30, "Female", "Turkey", 8500, "Boeing 737-800", "JUNIOR", Set.of("Turkish", "English")));
                pilotRepo.save(createPilot("F.O. Tom Cruise", 35, "Male", "USA", 9000, "Boeing 737-800", "JUNIOR", Set.of("English")));
                pilotRepo.save(createPilot("F.O. Natasha Romanoff", 33, "Female", "Russia", 8000, "Boeing 737-800", "JUNIOR", Set.of("Russian", "English")));

                // Restricted Junior (Ex: Newly trained, max 3000km)
                pilotRepo.save(createPilot("F.O. Rookie Joe", 24, "Male", "USA", 3000, "Boeing 737-800", "JUNIOR", Set.of("English")));

                // ==========================================
                // EMBRAER E195 (Short Range - 3,000km+)
                // ==========================================
                pilotRepo.save(createPilot("Cpt. Hans Muller", 50, "Male", "Germany", 5000, "Embraer E195", "SENIOR", Set.of("German", "English")));
                pilotRepo.save(createPilot("Cpt. Lars Ulrich", 52, "Male", "Denmark", 5000, "Embraer E195", "SENIOR", Set.of("Danish", "English")));
                pilotRepo.save(createPilot("Cpt. Canan Dag", 46, "Female", "Turkey", 4500, "Embraer E195", "SENIOR", Set.of("Turkish", "English")));

                pilotRepo.save(createPilot("F.O. Klaus Weber", 28, "Male", "Germany", 4000, "Embraer E195", "JUNIOR", Set.of("German", "English")));
                pilotRepo.save(createPilot("F.O. Zeynep Su", 27, "Female", "Turkey", 4000, "Embraer E195", "JUNIOR", Set.of("Turkish", "English")));
                pilotRepo.save(createPilot("F.O. Matteo Rossi", 29, "Male", "Italy", 4200, "Embraer E195", "JUNIOR", Set.of("Italian", "English")));

                pilotRepo.save(createPilot("Tr. Lena Meyer", 23, "Female", "Germany", 2000, "Embraer E195", "TRAINEE", Set.of("German", "English")));
                pilotRepo.save(createPilot("Tr. Arda Turan", 22, "Male", "Turkey", 2000, "Embraer E195", "TRAINEE", Set.of("Turkish", "English")));

                System.out.println("--- PILOT DB SEEDED WITH EXTENSIVE DATA ---");
            }
        };
    }

    private Pilot createPilot(String name, int age, String gender, String nationality, int range, String vehicle, String level, Set<String> langs) {
        Pilot p = new Pilot(name, age, gender, nationality, range, vehicle, level);
        p.setLanguages(langs);
        return p;
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

@RestController @RequestMapping("/api/pilots") @CrossOrigin(origins = "*")
class PilotController {
    private final PilotRepository repo;
    public PilotController(PilotRepository repo) { this.repo = repo; }
    @GetMapping public List<Pilot> getAll() { return repo.findAll(); }
}

interface PilotRepository extends JpaRepository<Pilot, Long> {}

@Entity @Table(name = "pilots")
class Pilot {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    private String name; private int age; private String gender; private String nationality;
    private int allowedRangeKm; private String allowedVehicleType; private String seniorityLevel;
    @ElementCollection(fetch = FetchType.EAGER) @CollectionTable(name = "pilot_languages", joinColumns = @JoinColumn(name = "pilot_id")) @Column(name = "language") private Set<String> languages;
    public Pilot() {}
    public Pilot(String name, int age, String gender, String nationality, int allowedRangeKm, String allowedVehicleType, String seniorityLevel) {
        this.name = name; this.age = age; this.gender = gender; this.nationality = nationality; this.allowedRangeKm = allowedRangeKm; this.allowedVehicleType = allowedVehicleType; this.seniorityLevel = seniorityLevel;
    }
    public Long getId() { return id; } public String getName() { return name; } public int getAge() { return age; } public String getGender() { return gender; } public String getNationality() { return nationality; } public int getAllowedRangeKm() { return allowedRangeKm; } public String getAllowedVehicleType() { return allowedVehicleType; } public String getSeniorityLevel() { return seniorityLevel; } public Set<String> getLanguages() { return languages; } public void setLanguages(Set<String> languages) { this.languages = languages; }
}