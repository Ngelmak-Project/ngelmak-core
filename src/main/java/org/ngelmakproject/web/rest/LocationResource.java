package org.ngelmakproject.web.rest;

import java.util.List;

import org.ngelmakproject.domain.Location;
import org.ngelmakproject.service.LocationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/locations")
public class LocationResource {

    @Autowired
    private LocationService locationService;

    @PostMapping
    public ResponseEntity<Location> create(@RequestBody Location dto) {
        return ResponseEntity.ok(locationService.createLocation(dto));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Location> getById(@PathVariable Long id) {
        return ResponseEntity.ok(locationService.getLocationById(id));
    }

    @GetMapping
    public ResponseEntity<List<Location>> getAll() {
        return ResponseEntity.ok(locationService.getAllLocations());
    }

    @GetMapping("/name/{name}")
    public ResponseEntity<List<Location>> getByName(@PathVariable String name) {
        return ResponseEntity.ok(locationService.getLocationsByName(name));
    }

    @GetMapping("/code/{code}")
    public ResponseEntity<Location> getByCode(@PathVariable String code) {
        return ResponseEntity.ok(locationService.getLocationByCode(code));
    }

    @GetMapping("/parent/{parentLocationId}")
    public ResponseEntity<List<Location>> getChildren(@PathVariable Long parentLocationId) {
        return ResponseEntity.ok(locationService.getChildLocations(parentLocationId));
    }

    @GetMapping("/top-level")
    public ResponseEntity<List<Location>> getTopLevel() {
        return ResponseEntity.ok(locationService.getTopLevelLocations());
    }

    @PutMapping("/{id}")
    public ResponseEntity<Location> update(@PathVariable Long id, @RequestBody Location dto) {
        return ResponseEntity.ok(locationService.updateLocation(id, dto));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        locationService.deleteLocation(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/descendants")
    public ResponseEntity<List<Location>> getDescendants(@PathVariable Long id) {
        return ResponseEntity.ok(locationService.getAllDescendants(id));
    }

    @GetMapping("/search")
    public ResponseEntity<List<Location>> search(@RequestParam String keyword) {
        return ResponseEntity.ok(locationService.searchLocations(keyword));
    }

    @GetMapping("/search/advanced")
    public ResponseEntity<List<Location>> searchAdvanced(@RequestParam String name, @RequestParam String type) {
        return ResponseEntity.ok(locationService.searchByNameAndType(name, type));
    }

    @GetMapping("/sorted/name")
    public ResponseEntity<List<Location>> sortByName() {
        return ResponseEntity.ok(locationService.getLocationsSortedByName());
    }

    @GetMapping("/sorted/type")
    public ResponseEntity<List<Location>> sortByType() {
        return ResponseEntity.ok(locationService.getLocationsSortedByType());
    }

    @PostMapping("/bulk")
    public ResponseEntity<List<Location>> createBulk(@RequestBody List<Location> dtos) {
        return ResponseEntity.ok(locationService.createBulkLocations(dtos));
    }

    @DeleteMapping("/bulk")
    public ResponseEntity<Void> deleteBulk(@RequestBody List<Long> locationIds) {
        locationService.bulkDeleteLocations(locationIds);
        return ResponseEntity.noContent().build();
    }

}
