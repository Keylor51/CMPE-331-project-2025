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
                CabinCrewMember chief = new CabinCrewMember("Sarah Connor", 35, "Female", "USA", "CHIEF", "SENIOR");
                chief.setLanguages(Set.of("English", "French"));
                chief.setAllowedVehicles(Set.of("Boeing 737", "Airbus A320"));
                
                CabinCrewMember regular = new CabinCrewMember("John Doe", 22, "Male", "UK", "REGULAR", "JUNIOR");
                regular.setLanguages(Set.of("English"));
                regular.setAllowedVehicles(Set.of("Boeing 737"));

                CabinCrewMember chef = new CabinCrewMember("Gordon Ramsay", 50, "Male", "UK", "CHEF", "SENIOR");
                chef.setLanguages(Set.of("English", "Italian"));
                chef.setAllowedVehicles(Set.of("Boeing 737", "Airbus A320"));
                chef.setChefRecipes(Set.of("Beef Wellington", "Lobster Risotto", "Tiramisu"));

                crewRepo.saveAll(List.of(chief, regular, chef));
                System.out.println("--- CABIN CREW DB SEEDED ---");
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
    // Getters/Setters
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