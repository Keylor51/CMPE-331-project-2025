package com.cmpe331.flight;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.CommandLineRunner;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FlightInfoApiTest {

    @Mock
    private FlightRepository flightRepo;
    @Mock
    private AirportRepository airportRepo;
    @Mock
    private VehicleTypeRepository vehicleRepo;
    @Mock
    private SharedFlightRepository sharedRepo;

    @InjectMocks
    private FlightController controller;
    
    @InjectMocks
    private FlightInfoApi api;

    @Test
    void testGetAllFlights() {
        when(flightRepo.findAll()).thenReturn(Collections.emptyList());
        List<Flight> result = controller.getAllFlights();
        assertTrue(result.isEmpty());
    }

    @Test
    void testGetFlightFound() {
        Flight f = new Flight("TK1", LocalDateTime.now(), 100, 500, null, null, null);
        when(flightRepo.findAll()).thenReturn(Collections.singletonList(f));
        
        ResponseEntity<Flight> response = controller.getFlight("TK1");
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("TK1", response.getBody().getFlightNumber());
    }

    @Test
    void testGetFlightNotFound() {
        when(flightRepo.findAll()).thenReturn(Collections.emptyList());
        ResponseEntity<Flight> response = controller.getFlight("TK99");
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }
    
    @Test
    void testGetSharedInfoFound() {
        SharedFlightDetails s = new SharedFlightDetails("TK1", "Lufthansa", "LH1", "Conn");
        when(sharedRepo.findById("TK1")).thenReturn(Optional.of(s));
        
        ResponseEntity<SharedFlightDetails> resp = controller.getSharedInfo("TK1");
        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertEquals("Lufthansa", resp.getBody().getPartnerCompanyName());
    }

    @Test
    void testGetSharedInfoNotFound() {
        when(sharedRepo.findById("TK99")).thenReturn(Optional.empty());
        ResponseEntity<SharedFlightDetails> resp = controller.getSharedInfo("TK99");
        assertEquals(HttpStatus.NOT_FOUND, resp.getStatusCode());
    }

    @Test
    void testInitData() throws Exception {
        when(airportRepo.count()).thenReturn(0L);
        CommandLineRunner runner = api.initDatabase(airportRepo, vehicleRepo, flightRepo, sharedRepo);
        runner.run();
        verify(airportRepo).saveAll(anyList());
        verify(vehicleRepo).saveAll(anyList());
        verify(flightRepo).saveAll(anyList());
    }

    @Test
    void testInitDataSkipped() throws Exception {
        when(airportRepo.count()).thenReturn(5L);
        CommandLineRunner runner = api.initDatabase(airportRepo, vehicleRepo, flightRepo, sharedRepo);
        runner.run();
        verify(airportRepo, never()).saveAll(anyList());
    }

    @Test
    void testEntityCoverage() {
        Airport a = new Airport();
        a = new Airport("IST", "Istanbul", "Ist", "TR");
        assertEquals("IST", a.getCode());
        assertEquals("Istanbul", a.getName());
        assertEquals("Ist", a.getCity());
        assertEquals("TR", a.getCountry());

        VehicleType v = new VehicleType();
        v = new VehicleType("Model", 100, 5, 95, "{}", "Menu");
        assertEquals("Model", v.getModelName());
        assertEquals(100, v.getTotalSeats());
        assertEquals("{}", v.getSeatingPlanConfig());
        assertEquals("Menu", v.getStandardMenuDescription());
        assertNull(v.getId());

        Flight f = new Flight();
        f = new Flight("F1", LocalDateTime.MIN, 60, 100, a, a, v);
        assertEquals("F1", f.getFlightNumber());
        assertEquals(LocalDateTime.MIN, f.getDateTime());
        assertEquals(a, f.getSource());
        assertEquals(a, f.getDestination());
        assertEquals(v, f.getVehicleType());
        assertEquals(100, f.getDistanceKm());
        
        SharedFlightDetails sf = new SharedFlightDetails();
        sf = new SharedFlightDetails("A", "B", "C", "D");
        assertEquals("A", sf.getLocalFlightNumber());
        assertEquals("B", sf.getPartnerCompanyName());
        assertEquals("C", sf.getPartnerFlightNumber());
        assertEquals("D", sf.getConnectingFlightInfo());
    }
    
    @Test
    void testSecurityConfigBeans() {
        SecurityConfig config = new SecurityConfig();
        assertNotNull(config.userDetailsService());
    }
}