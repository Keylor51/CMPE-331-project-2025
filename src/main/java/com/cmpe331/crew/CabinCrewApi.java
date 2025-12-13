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
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.bind.annotation.*;

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
                // --- CHIEFS (SENIORS) ---
                CabinCrewMember c1 = new CabinCrewMember("Sarah Connor", 35, "Female", "USA", "CHIEF", "SENIOR");
                c1.setAllowedVehicles(Set.of("Boeing 737", "Airbus A320")); 
                c1.setLanguages(Set.of("English", "French")); // Has languages

                CabinCrewMember c2 = new CabinCrewMember("Ellen Ripley", 38, "Female", "USA", "CHIEF", "SENIOR");
                c2.setAllowedVehicles(Set.of("Boeing 737")); 
                c2.setLanguages(Set.of("English", "Spanish")); // Has languages

                // --- CHEFS (COOKS) - Must have Recipes ---
                CabinCrewMember chef1 = new CabinCrewMember("Gordon Ramsay", 50, "Male", "UK", "CHEF", "SENIOR");
                chef1.setAllowedVehicles(Set.of("Boeing 737", "Airbus A320")); 
                chef1.setLanguages(Set.of("English"));
                chef1.setChefRecipes(Set.of("Beef Wellington", "Lobster Risotto")); // Has recipes

                CabinCrewMember chef2 = new CabinCrewMember("Massimo Bottura", 55, "Male", "Italy", "CHEF", "SENIOR");
                chef2.setAllowedVehicles(Set.of("Boeing 737")); 
                chef2.setLanguages(Set.of("Italian", "English"));
                chef2.setChefRecipes(Set.of("Lasagna", "Tiramisu", "Osso Buco")); // Has recipes

                CabinCrewMember chef3 = new CabinCrewMember("Jiro Ono", 80, "Male", "Japan", "CHEF", "SENIOR");
                chef3.setAllowedVehicles(Set.of("Airbus A320")); 
                chef3.setLanguages(Set.of("Japanese"));
                chef3.setChefRecipes(Set.of("Sushi Platter", "Miso Soup")); // Has recipes

                // --- REGULAR ATTENDANTS (JUNIORS) - Adding languages to ALL ---
                CabinCrewMember r1 = new CabinCrewMember("John Doe", 22, "Male", "UK", "REGULAR", "JUNIOR");
                r1.setAllowedVehicles(Set.of("Boeing 737"));
                r1.setLanguages(Set.of("English")); // Added

                CabinCrewMember r2 = new CabinCrewMember("Jane Smith", 23, "Female", "USA", "REGULAR", "JUNIOR");
                r2.setAllowedVehicles(Set.of("Boeing 737", "Airbus A320"));
                r2.setLanguages(Set.of("English", "German")); // Added

                CabinCrewMember r3 = new CabinCrewMember("Alice Wonderland", 21, "Female", "UK", "REGULAR", "JUNIOR");
                r3.setAllowedVehicles(Set.of("Boeing 737"));
                r3.setLanguages(Set.of("English", "French")); // Added

                CabinCrewMember r4 = new CabinCrewMember("Bob Builder", 25, "Male", "USA", "REGULAR", "JUNIOR");
                r4.setAllowedVehicles(Set.of("Boeing 737", "Airbus A320"));
                r4.setLanguages(Set.of("English", "Spanish")); // Added

                CabinCrewMember r5 = new CabinCrewMember("Charlie Brown", 24, "Male", "Canada", "REGULAR", "JUNIOR");
                r5.setAllowedVehicles(Set.of("Boeing 737"));
                r5.setLanguages(Set.of("English", "French")); // Added

                CabinCrewMember r6 = new CabinCrewMember("Diana Prince", 26, "Female", "Greece", "REGULAR", "JUNIOR");
                r6.setAllowedVehicles(Set.of("Boeing 737"));
                r6.setLanguages(Set.of("Greek", "English")); // Added

                CabinCrewMember r7 = new CabinCrewMember("Peter Parker", 20, "Male", "USA", "REGULAR", "JUNIOR");
                r7.setAllowedVehicles(Set.of("Airbus A320"));
                r7.setLanguages(Set.of("English")); // Added

                crewRepo.saveAll(List.of(c1, c2, chef1, chef2, chef3, r1, r2, r3, r4, r5, r6, r7));
                System.out.println("--- CABIN CREW DB SEEDED WITH FULL DATA ---");
            }
        };
    }
}

// ... (SecurityConfig, Controller, Repository, Entity remain unchanged) ...
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
@RequestMapping("/api/cabin-crew")
@CrossOrigin(origins = "*")
class CabinCrewController {
    private final CabinCrewRepository crewRepo;
    public CabinCrewController(CabinCrewRepository crewRepo) { this.crewRepo = crewRepo; }
    @GetMapping
    public List<CabinCrewMember> getAllCrew() { return crewRepo.findAll(); }
    @GetMapping("/chefs")
    public List<CabinCrewMember> getChefs() { return crewRepo.findByType("CHEF"); }
}

interface CabinCrewRepository extends JpaRepository<CabinCrewMember, Long> {
    List<CabinCrewMember> findByType(String type);
}

@Entity @Table(name = "cabin_crew")
class CabinCrewMember {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    private String name; private int age; private String gender; private String nationality;
    private String type; private String seniority;
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "crew_languages", joinColumns = @JoinColumn(name = "crew_id"))
    @Column(name = "language") private Set<String> languages;
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "crew_vehicles", joinColumns = @JoinColumn(name = "crew_id"))
    @Column(name = "vehicle_type") private Set<String> allowedVehicles;
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "chef_recipes", joinColumns = @JoinColumn(name = "crew_id"))
    @Column(name = "recipe_name") private Set<String> chefRecipes;

    public CabinCrewMember() {}
    public CabinCrewMember(String name, int age, String gender, String nationality, String type, String seniority) {
        this.name = name; this.age = age; this.gender = gender; this.nationality = nationality; this.type = type; this.seniority = seniority;
    }
    public Long getId() { return id; }
    public String getName() { return name; }
    public int getAge() { return age; }
    public String getGender() { return gender; }
    public String getNationality() { return nationality; }
    public String getType() { return type; }
    public String getSeniority() { return seniority; }
    public Set<String> getLanguages() { return languages; }
    public void setLanguages(Set<String> languages) { this.languages = languages; }
    public Set<String> getAllowedVehicles() { return allowedVehicles; }
    public void setAllowedVehicles(Set<String> allowedVehicles) { this.allowedVehicles = allowedVehicles; }
    public Set<String> getChefRecipes() { return chefRecipes; }
    public void setChefRecipes(Set<String> chefRecipes) { this.chefRecipes = chefRecipes; }
}