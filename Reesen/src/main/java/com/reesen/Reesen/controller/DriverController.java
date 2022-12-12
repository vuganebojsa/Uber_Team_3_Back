package com.reesen.Reesen.controller;


import com.reesen.Reesen.dto.*;
import com.reesen.Reesen.mockup.*;
import com.reesen.Reesen.model.*;
import com.reesen.Reesen.model.paginated.Paginated;
import com.reesen.Reesen.service.interfaces.*;
import org.hibernate.jdbc.Work;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.swing.text.html.Option;
import java.sql.Date;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@CrossOrigin
@RestController
@RequestMapping(value = "api/driver")
public class DriverController {
    private final IDriverService driverService;
    private final IDocumentService documentService;
    private final IVehicleService vehicleService;
    private final IWorkingHoursService workingHoursService;
    private final ILocationService locationService;

    @Autowired
    public DriverController(IDriverService driverService,
                            IDocumentService documentService,
                            IVehicleService vehicleService,
                            IWorkingHoursService workingHoursService,
                            ILocationService locationService){
        this.driverService = driverService;
        this.documentService = documentService;
        this.vehicleService = vehicleService;
        this.workingHoursService = workingHoursService;
        this.locationService = locationService;
    }


    /**
     *
     *  PUT MAPPINGS
     *
     * **/
    @PutMapping(value = "/{id}")
    public ResponseEntity<CreatedDriverDTO> updateDriver(@RequestBody DriverDTO driverDTO, @PathVariable Long id){

        if(this.driverService.findOne(id).isEmpty()) return new ResponseEntity<>(HttpStatus.NOT_FOUND);

        Driver driver = this.driverService.findByEmail(driverDTO.getEmail());
        if(driver!= null && !driver.getId().toString().equals(id.toString())) {
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }
        driver = this.driverService.getDriverFromDriverDTO(id, driverDTO);
        this.driverService.save(driver);
        CreatedDriverDTO updatedDriver = new CreatedDriverDTO(driver);
        return new ResponseEntity<>(updatedDriver, HttpStatus.OK);



    }

            /**
             *
             *  PUT Vehicle
             *
             * **/
    @PutMapping(value = "/{id}/vehicle")
    public ResponseEntity<VehicleDTO> updateVehicle(@RequestBody VehicleDTO vehicleDTO, @PathVariable("id") Long driverId){

        if(driverId < 1) return new ResponseEntity<>(HttpStatus.BAD_REQUEST);

        Optional<Driver> driver = this.driverService.findOne(driverId);
        if(driver.isEmpty()) return new ResponseEntity<>(HttpStatus.NOT_FOUND);

        Vehicle vehicle = this.driverService.getVehicle(driverId);
        // if ! exists -> create
        if(vehicle == null){
            vehicle = this.vehicleService.createVehicle(vehicleDTO, driver.get());
        }else{
            // if exists -> edit
            vehicle = this.vehicleService.editVehicle(vehicle, vehicleDTO);
        }
        vehicle = this.vehicleService.save(vehicle);
        driver.get().setVehicle(vehicle);
        this.driverService.save(driver.get());

        return new ResponseEntity<>(new VehicleDTO(vehicle), HttpStatus.OK);
    }

            /**
             *
             *  PUT Working Hours
             *
             * **/

    @PutMapping(value = "/working-hour/{working-hour-id}")
    public ResponseEntity<WorkingHoursDTO> changeWorkingHours(
            @RequestBody WorkingHoursDTO workingHoursDTO,
            @PathVariable("working-hour-id") Long workingHourId
    ){
        if(workingHourId < 1 ) return new ResponseEntity<>(HttpStatus.BAD_REQUEST);

        Optional<WorkingHours> workingHours = this.workingHoursService.findOne(workingHourId);
        if(workingHours.isEmpty()) return new ResponseEntity<>(HttpStatus.NOT_FOUND);

        WorkingHours updatedWH = this.workingHoursService.editWorkingHours(workingHours.get(), workingHoursDTO);
        this.workingHoursService.save(updatedWH);

        return new ResponseEntity<>(new WorkingHoursDTO(updatedWH), HttpStatus.OK);
    }
    /**
     *
     *  POST MAPPINGS
     *
     * **/
    @PostMapping
    public ResponseEntity<CreatedDriverDTO> createDriver(@RequestBody DriverDTO driverDTO){

        if(this.driverService.findByEmail(driverDTO.getEmail()) != null)
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);


        CreatedDriverDTO createdDriverDTO = this.driverService.createDriverDTO(driverDTO);
        return new ResponseEntity<>(createdDriverDTO, HttpStatus.OK);
    }

            /**
             *
             *  POST Documents
             *
             * **/

    @PostMapping(value = "/{id}/documents")
    public ResponseEntity<DocumentDTO> addDocument(@RequestBody DocumentDTO documentDTO, @PathVariable("id") Long driverId){

        if(driverId < 1) return new ResponseEntity<>(HttpStatus.BAD_REQUEST);

        Optional<Driver> driver = this.driverService.findOne(driverId);
        if(driver.isEmpty()) return new ResponseEntity<>(HttpStatus.NOT_FOUND);

        Document document = new Document(documentDTO.getName(), documentDTO.getDocumentImage(), driver.get());
        document = this.documentService.save(document);

        return new ResponseEntity<>(new DocumentDTO(document), HttpStatus.OK);

    }

            /**
             *
             *  POST Vehicles
             *
             * **/


    @PostMapping(value = "/{id}/vehicle")
    public ResponseEntity<VehicleDTO> addVehicle(@RequestBody VehicleDTO vehicleDTO, @PathVariable("id") Long driverId){

        if(driverId < 1) return new ResponseEntity<>(HttpStatus.BAD_REQUEST);

        Optional<Driver> driver = this.driverService.findOne(driverId);
        if(driver.isEmpty()) return new ResponseEntity<>(HttpStatus.NOT_FOUND);


        Location location = this.locationService.getLocation(vehicleDTO.getCurrentLocation());
        Vehicle vehicle = this.vehicleService.createVehicle(vehicleDTO,location);
        vehicle.setDriver(driver.get());

        driver.get().setVehicle(vehicle);
        this.driverService.save(driver.get());


        return new ResponseEntity<>(new VehicleDTO(vehicle), HttpStatus.OK);
    }

    

            /**
             *
             *  POST Working Hours
             *
             * **/
    @PostMapping(value = "/{id}/working-hour")
    public ResponseEntity<WorkingHoursDTO> createWorkingHours(@RequestBody WorkingHoursDTO workingHoursDTO, @PathVariable("id") Long driverId){

        if(driverId < 1) return new ResponseEntity<>(HttpStatus.BAD_REQUEST);

        Optional<Driver> driver = this.driverService.findOne(driverId);
        if(driver.isEmpty()) return new ResponseEntity<>(HttpStatus.NOT_FOUND);

        WorkingHours workingHours = this.workingHoursService.createWorkingHours(workingHoursDTO, driver.get());
        workingHours = this.workingHoursService.save(workingHours);

        return new ResponseEntity<>(new WorkingHoursDTO(workingHours), HttpStatus.OK);
    }

    /**
     *
     *  GET MAPPINGS
     *
     * **/

    @GetMapping
    public ResponseEntity<Paginated<CreatedDriverDTO>> getDrivers(
            Pageable page
    ){
        Page<Driver> drivers = this.driverService.findAll(page);
        Set<CreatedDriverDTO> driverDTOS = new HashSet<>();
        for(Driver driver: drivers){
            driverDTOS.add(new CreatedDriverDTO(driver));
        }
        return new ResponseEntity<>(
                new Paginated<CreatedDriverDTO>(drivers.getNumberOfElements(), driverDTOS), HttpStatus.OK);
    }


    @GetMapping(value = "/{id}")
    public ResponseEntity<CreatedDriverDTO> getDriver(@PathVariable Long id){

        if(id < 1) return new ResponseEntity<>(HttpStatus.BAD_REQUEST);

        Optional<Driver> driver = this.driverService.findOne(id);
        if(driver.isEmpty()) return new ResponseEntity<>(HttpStatus.NOT_FOUND);

        CreatedDriverDTO driverDTO = new CreatedDriverDTO(driver.get());

        return new ResponseEntity<>(driverDTO, HttpStatus.OK);

    }

            /**
             *
             *  GET DOCUMENTS
             *
             * **/

    @GetMapping(value = "/{id}/documents")
    public ResponseEntity<Set<DocumentDTO>> getDocument(@PathVariable("id") Long id){

        if(id < 1) return new ResponseEntity<>(HttpStatus.BAD_REQUEST);

        Optional<Driver> driver = this.driverService.findOne(id);
        if(driver.isEmpty()) return new ResponseEntity<>(HttpStatus.NOT_FOUND);

        List<Document> documents = this.documentService.findAllByDriverId(driver.get().getId());

        return new ResponseEntity<>(this.documentService.getDocumentDTOS(documents), HttpStatus.OK);

    }
            /*
            *
            *  GET VEHICLE
            *
            * **/
    @GetMapping(value = "/{id}/vehicle")
    public ResponseEntity<VehicleDTO> getVehicle(@PathVariable("id") Long id){
        if(id < 1 ) return new ResponseEntity<>(HttpStatus.BAD_REQUEST);

        if(this.driverService.findOne(id).isEmpty()) return new ResponseEntity<>(HttpStatus.NOT_FOUND);

        Vehicle vehicle = this.driverService.getVehicle(id);
        if(vehicle == null) return new ResponseEntity<>(HttpStatus.NOT_FOUND);

        Optional<VehicleType> type = this.vehicleService.findType(vehicle.getId());
        Location location = this.vehicleService.findLocation(vehicle.getId());
        type.ifPresent(vehicle::setType);
        vehicle.setCurrentLocation(location);

        return new ResponseEntity<>(new VehicleDTO(vehicle), HttpStatus.OK);
    }
            /*
             *
             *  GET Working Hours
             *
             * **/

    @GetMapping(value = "/{id}/working-hour")
    public ResponseEntity<Paginated<WorkingHoursDTO>> getWorkingHours(
            Pageable page,
            @PathVariable("id") Long driverId,
            @RequestParam("from") LocalDateTime from,
            @RequestParam("to") LocalDateTime to
    )
    {
        Optional<Driver> driver = this.driverService.findOne(driverId);
        if(driver.isEmpty()) return new ResponseEntity<>(HttpStatus.NOT_FOUND);

        Page<WorkingHours> workingHours;
        workingHours = this.workingHoursService.findAll(driverId, page, from, to);

        Set<WorkingHoursDTO> workingHoursDTOS = new HashSet<>();
        for(WorkingHours workingHour: workingHours){
            workingHoursDTOS.add(new WorkingHoursDTO(workingHour));
        }
        // TODO check what getNumberOfElements returns
        return new ResponseEntity<>(new Paginated<WorkingHoursDTO>
                (workingHours.getNumberOfElements(), workingHoursDTOS),
                HttpStatus.OK);
    }

    @GetMapping(value = "/working-hour/{working-hour-id}")
    public ResponseEntity<WorkingHoursDTO> getDetailsAboutWorkingHours(
            @PathVariable("working-hour-id") Long workingHourId)
    {

        if(workingHourId < 1) return new ResponseEntity<>(HttpStatus.BAD_REQUEST);

        Optional<WorkingHours> workingHours = this.workingHoursService.findOne(workingHourId);
        if(workingHours.isEmpty()) return new ResponseEntity<>(HttpStatus.NOT_FOUND);

        return new ResponseEntity<>(new WorkingHoursDTO(workingHours.get()), HttpStatus.OK);
    }

    /*
     *
     *  GET Rides
     *
     * **/

    @GetMapping(value = "/{id}/ride")
    public ResponseEntity<Paginated<DriverRideMockup>> getRides(
            @PathVariable("id") Long driverId,
            @RequestParam("page") int page,
            @RequestParam("size") int size,
            @RequestParam("sort") String sort,
            @RequestParam("from") String from,
            @RequestParam("to") String to)
    {

        Paginated<DriverRideMockup> driverRidePaginated = new Paginated<DriverRideMockup>(243);
        DriverRideMockup ride = new DriverRideMockup(Long.parseLong("123"));
        driverRidePaginated.add(ride);

        return new ResponseEntity<>(driverRidePaginated, HttpStatus.OK);
    }

    /**
     *
     *  DELETE MAPPINGS
     *
     * **/
    @DeleteMapping(value = "/document/{document-id}")
    public ResponseEntity<String> deleteDocuments(@PathVariable("document-id") Long id){

        if(id < 1) return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        if(this.documentService.findOne(id).isEmpty()) return new ResponseEntity<>(HttpStatus.NOT_FOUND);

        this.documentService.delete(id);
        return new ResponseEntity<>("Driver document deleted successfully", HttpStatus.NO_CONTENT);
    }

}
