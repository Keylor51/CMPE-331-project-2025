package com.cmpe331.pilot;

import jakarta.persistence.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

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
                Pilot p1 = new Pilot("Cpt. John Smith", 45, "Male", "USA", 10000, "Boeing 737", "SENIOR");
                p1.setLanguages(Set.of("English", "Spanish"));

                Pilot p2 = new Pilot("Ayse Yilmaz", 29, "Female", "Turkey", 5000, "Boeing 737", "JUNIOR");
                p2.setLanguages(Set.of("Turkish", "English", "German"));

                Pilot p3 = new Pilot("Hans Mueller", 24, "Male", "Germany", 1500, "Airbus A320", "TRAINEE");
                p3.setLanguages(Set.of("German", "English"));

                pilotRepo.saveAll(List.of(p1, p2, p3));
                System.out.println("--- PILOT DB SEEDED ---");
            }
        };
    }
}

@RestController
@RequestMapping("/api/pilots")
@CrossOrigin(origins = "*")
class PilotController {

    private final PilotRepository pilotRepo;

    public PilotController(PilotRepository pilotRepo) {
        this.pilotRepo = pilotRepo;
    }

    @GetMapping
    public List<Pilot> getAllPilots() {
        return pilotRepo.findAll();
    }

    // FIXED: Added ("vehicleType") to prevent crash
    @GetMapping("/vehicle/{vehicleType}")
    public List<Pilot> getPilotsByVehicle(@PathVariable("vehicleType") String vehicleType) {
        return pilotRepo.findByAllowedVehicleType(vehicleType);
    }
}

interface PilotRepository extends JpaRepository<Pilot, Long> {
    List<Pilot> findByAllowedVehicleType(String vehicleType);
}

@Entity
@Table(name = "pilots")
class Pilot {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String name;
    private int age;
    private String gender;
    private String nationality;
    private int allowedRangeKm; 
    private String allowedVehicleType;
    private String seniorityLevel;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "pilot_languages", joinColumns = @JoinColumn(name = "pilot_id"))
    @Column(name = "language")
    private Set<String> languages;

    public Pilot() {}
    public Pilot(String name, int age, String gender, String nationality, int allowedRangeKm, String allowedVehicleType, String seniorityLevel) {
        this.name = name;
        this.age = age;
        this.gender = gender;
        this.nationality = nationality;
        this.allowedRangeKm = allowedRangeKm;
        this.allowedVehicleType = allowedVehicleType;
        this.seniorityLevel = seniorityLevel;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public int getAge() { return age; }
    public String getGender() { return gender; }
    public String getNationality() { return nationality; }
    public int getAllowedRangeKm() { return allowedRangeKm; }
    public String getAllowedVehicleType() { return allowedVehicleType; }
    public String getSeniorityLevel() { return seniorityLevel; }
    public Set<String> getLanguages() { return languages; }
    public void setLanguages(Set<String> languages) { this.languages = languages; }
}