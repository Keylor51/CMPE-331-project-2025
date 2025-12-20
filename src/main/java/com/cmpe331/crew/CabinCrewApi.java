package com.cmpe331.crew;

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

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.springframework.security.config.Customizer.withDefaults;

@SpringBootApplication
public class CabinCrewApi {
    public static void main(String[] args) {
        System.setProperty("server.port", "8083");
        System.setProperty("spring.datasource.url", "jdbc:sqlite:cabin_db.sqlite");
        SpringApplication.run(CabinCrewApi.class, args);
    }

    @Bean
    CommandLineRunner initCrew(CabinCrewRepository crewRepo) {
        return args -> {
            if (crewRepo.count() == 0) {
                // ==========================================
                // CHIEFS (Cabin Chiefs - 8 people)
                // ==========================================
                saveCrew(crewRepo, "Zeynep Demir", 38, "Female", "Turkey", "CHIEF", "SENIOR", Set.of("Boeing 787 Dreamliner", "Boeing 737-800"), null);
                saveCrew(crewRepo, "Michael Scott", 45, "Male", "USA", "CHIEF", "SENIOR", Set.of("Boeing 787 Dreamliner"), null);
                saveCrew(crewRepo, "Fatma Yilmaz", 40, "Female", "Turkey", "CHIEF", "SENIOR", Set.of("Boeing 737-800", "Embraer E195"), null);
                saveCrew(crewRepo, "Hans Gruber", 42, "Male", "Germany", "CHIEF", "SENIOR", Set.of("Embraer E195", "Boeing 787 Dreamliner"), null);
                saveCrew(crewRepo, "Elena Ivanova", 39, "Female", "Russia", "CHIEF", "SENIOR", Set.of("Boeing 787 Dreamliner"), null);
                saveCrew(crewRepo, "Kenji Tanaka", 44, "Male", "Japan", "CHIEF", "SENIOR", Set.of("Boeing 787 Dreamliner"), null);
                saveCrew(crewRepo, "Maria Gonzalez", 37, "Female", "Spain", "CHIEF", "SENIOR", Set.of("Boeing 737-800"), null);
                saveCrew(crewRepo, "David Kim", 41, "Male", "Korea", "CHIEF", "SENIOR", Set.of("Boeing 787 Dreamliner", "Boeing 737-800"), null);

                // ==========================================
                // CHEFS (Cooks - 6 people)
                // ==========================================
                saveCrew(crewRepo, "Chef Luigi", 40, "Male", "Italy", "CHEF", "SENIOR", Set.of("Boeing 787 Dreamliner", "Boeing 737-800"), Set.of("Truffle Risotto", "Lasagna Bolognese"));
                saveCrew(crewRepo, "Chef Akira", 35, "Male", "Japan", "CHEF", "SENIOR", Set.of("Boeing 787 Dreamliner"), Set.of("Sushi Platter", "Wagyu Steak", "Miso Soup"));
                saveCrew(crewRepo, "Chef Pierre", 38, "Male", "France", "CHEF", "SENIOR", Set.of("Boeing 787 Dreamliner"), Set.of("Coq au Vin", "Creme Brulee"));
                saveCrew(crewRepo, "Chef Burak", 32, "Male", "Turkey", "CHEF", "SENIOR", Set.of("Boeing 737-800", "Boeing 787 Dreamliner"), Set.of("Iskender Kebab", "Baklava"));
                saveCrew(crewRepo, "Chef Gordon", 45, "Male", "UK", "CHEF", "SENIOR", Set.of("Boeing 787 Dreamliner"), Set.of("Beef Wellington", "Fish and Chips"));
                saveCrew(crewRepo, "Chef Julia", 36, "Female", "USA", "CHEF", "SENIOR", Set.of("Boeing 787 Dreamliner"), Set.of("Lobster Bisque", "Cheesecake"));

                // ==========================================
                // REGULAR ATTENDANTS (Officers - 60+ people)
                // ==========================================
                String[] baseNames = {"Ali", "Veli", "Ayse", "Fatma", "John", "Jane", "Hans", "Helga", "Yuki", "Hiro", "Elena", "Sofia", "Mehmet", "Can", "Elif", "Selin", "Tom", "Jerry", "Mickey", "Minnie"};
                String[] surnames = {"Kaya", "Demir", "Celik", "Yilmaz", "Smith", "Doe", "Muller", "Weber", "Sato", "Tanaka", "Ivanov", "Popov", "Ozturk", "Arslan", "Koc", "Aydin", "Brown", "Wilson", "Mouse", "Duck"};
                
                int counter = 0;
                // Creating a large pool by combining names
                for (String name : baseNames) {
                    for (String surname : surnames) {
                        counter++;
                        // Adding some randomness: Vehicle types and languages
                        Set<String> vehicles = new HashSet<>();
                        
                        // 50% know Embraer
                        if (counter % 2 == 0) vehicles.add("Embraer E195");
                        
                        // 70% know Boeing 737 (Most common)
                        if (counter % 3 != 0) vehicles.add("Boeing 737-800");
                        
                        // 40% know Boeing 787 (Long range)
                        if (counter % 5 <= 2) vehicles.add("Boeing 787 Dreamliner");

                        // If none, add at least one
                        if(vehicles.isEmpty()) vehicles.add("Boeing 737-800");

                        String gender = (counter % 2 == 0) ? "Female" : "Male";
                        String seniority = (counter % 6 == 0) ? "SENIOR" : "JUNIOR"; // 1 out of every 6 should be Senior
                        
                        saveCrew(crewRepo, name + " " + surname, 20 + (counter % 20), gender, "Global", "REGULAR", seniority, vehicles, null);
                        
                        // 60 people is enough, break the loop
                        if(counter >= 60) break;
                    }
                    if(counter >= 60) break;
                }

                System.out.println("--- CREW DB SEEDED WITH MASSIVE POOL (" + counter + " regulars) ---");
            }
        };
    }

    private void saveCrew(CabinCrewRepository repo, String name, int age, String gender, String nat, String type, String seniority, Set<String> vehicles, Set<String> recipes) {
        CabinCrewMember c = new CabinCrewMember(name, age, gender, nat, type, seniority);
        c.setLanguages(Set.of("English", "Turkish"));
        c.setAllowedVehicles(vehicles);
        if(recipes != null) c.setChefRecipes(recipes);
        repo.save(c);
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

@RestController @RequestMapping("/api/cabin-crew") @CrossOrigin(origins = "*")
class CabinCrewController {
    private final CabinCrewRepository repo;
    public CabinCrewController(CabinCrewRepository repo) { this.repo = repo; }
    @GetMapping public List<CabinCrewMember> getAll() { return repo.findAll(); }
}

interface CabinCrewRepository extends JpaRepository<CabinCrewMember, Long> {}

@Entity @Table(name = "cabin_crew")
class CabinCrewMember {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    private String name; private int age; private String gender; private String nationality;
    private String type; private String seniority;
    @ElementCollection(fetch = FetchType.EAGER) @CollectionTable(name = "crew_languages", joinColumns = @JoinColumn(name = "crew_id")) @Column(name = "language") private Set<String> languages;
    @ElementCollection(fetch = FetchType.EAGER) @CollectionTable(name = "crew_vehicles", joinColumns = @JoinColumn(name = "crew_id")) @Column(name = "vehicle_type") private Set<String> allowedVehicles;
    @ElementCollection(fetch = FetchType.EAGER) @CollectionTable(name = "chef_recipes", joinColumns = @JoinColumn(name = "crew_id")) @Column(name = "recipe_name") private Set<String> chefRecipes;

    public CabinCrewMember() {}
    public CabinCrewMember(String name, int age, String gender, String nationality, String type, String seniority) {
        this.name = name; this.age = age; this.gender = gender; this.nationality = nationality; this.type = type; this.seniority = seniority;
    }
    public Long getId() { return id; } public String getName() { return name; } public int getAge() { return age; } public String getGender() { return gender; } public String getNationality() { return nationality; } public String getType() { return type; } public String getSeniority() { return seniority; } public Set<String> getLanguages() { return languages; } public void setLanguages(Set<String> languages) { this.languages = languages; } public Set<String> getAllowedVehicles() { return allowedVehicles; } public void setAllowedVehicles(Set<String> allowedVehicles) { this.allowedVehicles = allowedVehicles; } public Set<String> getChefRecipes() { return chefRecipes; } public void setChefRecipes(Set<String> chefRecipes) { this.chefRecipes = chefRecipes; }
}