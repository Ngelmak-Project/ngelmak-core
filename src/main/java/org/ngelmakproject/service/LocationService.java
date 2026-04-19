package org.ngelmakproject.service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.ngelmakproject.domain.Location;
import org.ngelmakproject.repository.LocationRepository;
import org.ngelmakproject.web.rest.errors.ResourceNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import jakarta.transaction.Transactional;

@Service
@Transactional
public class LocationService {

    @Autowired
    private LocationRepository locationRepository;

    // CREATE
    public Location createLocation(Location dto) {
        Location location = new Location();
        location.setCode(dto.getCode());
        location.setDescription(dto.getDescription());
        return locationRepository.save(location);
    }

    // READ
    public Location getLocationById(Long id) {
        return locationRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Location not found", "location", "notFound"));
    }

    public List<Location> getAllLocations() {
        return locationRepository.findAll();
    }

    public List<Location> getLocationsByName(String name) {
        return locationRepository.findByNameContainingIgnoreCase(name);
    }

    public List<Location> getLocationsByCode(String code) {
        return locationRepository.findByCodeContainingIgnoreCase(code);
    }

    public List<Location> getChildLocations(Long parentLocationId) {
        return locationRepository.findByParentLocationId(parentLocationId);
    }

    public Location getLocationByCode(String code) {
        return locationRepository.findByCodeIgnoreCase(code)
                .orElseThrow(() -> new ResourceNotFoundException("Location not found", "location", "notFound"));
    }

    public List<Location> getTopLevelLocations() {
        return locationRepository.findByParentLocationIdIsNull();
    }

    // UPDATE
    public Location updateLocation(Long id, Location dto) {
        Location location = getLocationById(id);
        location.setLabel(dto.getLabel());
        location.setCode(dto.getCode());
        location.setDescription(dto.getDescription());
        return locationRepository.save(location);
    }

    public Location updateLocationDescription(Long id, String description) {
        Location location = getLocationById(id);
        location.setDescription(description);
        return locationRepository.save(location);
    }

    // DELETE
    public void deleteLocation(Long id) {
        locationRepository.deleteById(id);
    }

    public List<Location> getAllDescendants(Long locationId) {
        List<Location> descendants = new ArrayList<>();
        Location location = getLocationById(locationId);
        descendants.add(location);
        
        List<Location> children = getChildLocations(locationId);
        for (Location child : children) {
            descendants.addAll(getAllDescendants(child.getId()));
        }
        
        return descendants;
    }

    // SEARCH
    public List<Location> searchLocations(String keyword) {
        return locationRepository.findByNameContainingIgnoreCaseOrCodeContainingIgnoreCase(keyword, keyword);
    }

    public List<Location> searchByNameAndType(String name, String type) {
        return locationRepository.findByNameContainingIgnoreCaseAndType(name, type);
    }

    // FILTERING
    public List<Location> getLocationsSortedByName() {
        return locationRepository.findAllByOrderByNameAsc();
    }

    public List<Location> getLocationsSortedByType() {
        return locationRepository.findAllByOrderByTypeAsc();
    }

    // BATCH OPERATIONS
    public List<Location> createBulkLocations(List<Location> dtos) {
        return dtos.stream()
                .map(this::createLocation)
                .collect(Collectors.toList());
    }

    public void bulkDeleteLocations(List<Long> locationIds) {
        locationRepository.deleteAllById(locationIds);
    }

    // STATISTICS
    public long getTotalLocationCount() {
        return locationRepository.count();
    }

    public long getLocationCountByType(String type) {
        return locationRepository.countByType(type);
    }

    public long getChildLocationCount(Long parentLocationId) {
        return locationRepository.countByParentLocationId(parentLocationId);
    }

    // VALIDATION
    public boolean locationExists(Long id) {
        return locationRepository.existsById(id);
    }

    public boolean locationExists(String code) {
        return locationRepository.existsByCodeIgnoreCase(code);
    }
}
