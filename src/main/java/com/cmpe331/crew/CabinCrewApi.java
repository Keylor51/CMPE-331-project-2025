package com.cmpe331.crew;

import jakarta.persistence.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

@SpringBootApplication
public class CabinCrewApi {

    public static void main(String[] args) {
        // Run on Port 8083
        System.setProperty("server.port", "8083");
        System.setProperty("spring.datasource.url", "jdbc:sqlite:cabin_db.sqlite");
        SpringApplication.run(CabinCrewApi.class, args);
    }

    /**
     * DATA SEEDER
     */
    @Bean
    CommandLineRunner initCrew(CabinCrewRepository crewRepo) {
        return args -> {
            if (crewRepo.count() == 0) {
                // 1. Chief Attendant (Senior)
                CabinCrewMember chief = new CabinCrewMember("Sarah Connor", 35, "Female", "USA", "CHIEF", "SENIOR");
                chief.setLanguages(Set.of("English", "French"));
                chief.setAllowedVehicles(Set.of("Boeing 737", "Airbus A320"));
                
                // 2. Regular Attendant (Junior)
                CabinCrewMember regular = new CabinCrewMember("John Doe", 22, "Male", "UK", "REGULAR", "JUNIOR");
                regular.setLanguages(Set.of("English"));
                regular.setAllowedVehicles(Set.of("Boeing 737"));

                // 3. Chef (Cook) - Has Recipes
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

// ------------------- CONTROLLER -------------------

@RestController
@RequestMapping("/api/cabin-crew")
@CrossOrigin(origins = "*")
class CabinCrewController {

    private final CabinCrewRepository crewRepo;

    public CabinCrewController(CabinCrewRepository crewRepo) {
        this.crewRepo = crewRepo;
    }

    @GetMapping
    public List<CabinCrewMember> getAllCrew() {
        return crewRepo.findAll();
    }

    // Find chefs specifically to get their recipes
    @GetMapping("/chefs")
    public List<CabinCrewMember> getChefs() {
        return crewRepo.findByType("CHEF");
    }
}

// ------------------- REPOSITORY -------------------

interface CabinCrewRepository extends JpaRepository<CabinCrewMember, Long> {
    List<CabinCrewMember> findByType(String type);
}

// ------------------- ENTITIES -------------------

@Entity
@Table(name = "cabin_crew")
class CabinCrewMember {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private int age;
    private String gender;
    private String nationality;
    
    // CHIEF, REGULAR, or CHEF
    private String type;
    
    // SENIOR, JUNIOR
    private String seniority;

    // ElementCollection for Languages
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "crew_languages", joinColumns = @JoinColumn(name = "crew_id"))
    @Column(name = "language")
    private Set<String> languages;

    // ElementCollection for Allowed Vehicles (Multiple supported)
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "crew_vehicles", joinColumns = @JoinColumn(name = "crew_id"))
    @Column(name = "vehicle_type")
    private Set<String> allowedVehicles;

    // ElementCollection for Recipes (Only valid if type == CHEF)
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "chef_recipes", joinColumns = @JoinColumn(name = "crew_id"))
    @Column(name = "recipe_name")
    private Set<String> chefRecipes;

    public CabinCrewMember() {}

    public CabinCrewMember(String name, int age, String gender, String nationality, String type, String seniority) {
        this.name = name;
        this.age = age;
        this.gender = gender;
        this.nationality = nationality;
        this.type = type;
        this.seniority = seniority;
    }

    // Getters and Setters
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