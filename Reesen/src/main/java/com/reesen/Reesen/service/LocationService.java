package com.reesen.Reesen.service;

import com.reesen.Reesen.dto.CurrentLocationDTO;
import com.reesen.Reesen.model.Location;
import com.reesen.Reesen.model.Route;
import com.reesen.Reesen.repository.LocationRepository;
import com.reesen.Reesen.service.interfaces.ILocationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Iterator;
import java.util.LinkedHashSet;

@Service
public class LocationService implements ILocationService {
    private final LocationRepository locationRepository;

    @Autowired
    public LocationService(LocationRepository locationRepository) {
        this.locationRepository = locationRepository;
    }

    @Override
    public Location findOne(Long id) {
        return null;
    }

    @Override
    public Location save(Location location) {
        return null;
    }

    @Override
    public Location getLocation(CurrentLocationDTO locationDTO){
        Location location = new Location();
        location.setLongitude(locationDTO.getLongitude());
        location.setLatitude(locationDTO.getLatitude());
        location.setAddress(locationDTO.getAddress());
        return this.locationRepository.save(location);
    }

    @Override
    public Location getLastLocation(LinkedHashSet<Route> locations){
        Iterator<Route> iterator = locations.iterator();
        Route lastRoute = null;
        while (iterator.hasNext()) {
            lastRoute = iterator.next();
        }
        return lastRoute.getDestination();
    }

    @Override
    public Location getFirstLocation(LinkedHashSet<Route> locations){
        Iterator<Route> iterator = locations.iterator();
        return iterator.next().getDeparture();
    }

}
