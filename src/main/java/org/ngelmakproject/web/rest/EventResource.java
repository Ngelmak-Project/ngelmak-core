package org.ngelmakproject.web.rest;

import java.util.List;
import java.util.Set;

import org.ngelmakproject.domain.Event;
import org.ngelmakproject.domain.Event.EventStatus;
import org.ngelmakproject.domain.File;
import org.ngelmakproject.domain.Location;
import org.ngelmakproject.service.EventService;
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
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/events")
public class EventResource {

    @Autowired
    private EventService eventService;

    @PostMapping
    public ResponseEntity<Event> create(@RequestBody Event dto, 
                                       @RequestParam List<MultipartFile> medias,
                                       @RequestParam List<MultipartFile> covers) {
        return ResponseEntity.ok(eventService.createEvent(dto, medias, covers));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Event> getById(@PathVariable Long id) {
        return ResponseEntity.ok(eventService.getEventById(id));
    }

    @GetMapping
    public ResponseEntity<List<Event>> getAll() {
        return ResponseEntity.ok(eventService.getAllEvents());
    }

    @GetMapping("/status/{status}")
    public ResponseEntity<List<Event>> getByStatus(@PathVariable EventStatus status) {
        return ResponseEntity.ok(eventService.getEventsByStatus(status));
    }

    @GetMapping("/location/{locationId}")
    public ResponseEntity<List<Event>> getByLocation(@PathVariable Long locationId) {
        return ResponseEntity.ok(eventService.getEventsByLocation(locationId));
    }

    @GetMapping("/creator/{userId}")
    public ResponseEntity<List<Event>> getByCreator(@PathVariable Long userId) {
        return ResponseEntity.ok(eventService.getEventsByCreator(userId));
    }

    @GetMapping("/published")
    public ResponseEntity<List<Event>> getPublished() {
        return ResponseEntity.ok(eventService.getPublishedEvents());
    }

    @PutMapping("/{id}")
    public ResponseEntity<Event> update(@PathVariable Long id, 
                                       @RequestBody Event dto,
                                       @RequestParam List<MultipartFile> medias,
                                       @RequestParam List<File> deletedMedias,
                                       @RequestParam List<MultipartFile> covers) {
        return ResponseEntity.ok(eventService.updateEvent(id, dto, medias, deletedMedias, covers));
    }

    @PutMapping("/{id}/status/{status}")
    public ResponseEntity<Event> updateStatus(@PathVariable Long id, @PathVariable EventStatus status) {
        return ResponseEntity.ok(eventService.updateEventStatus(id, status));
    }

    @PutMapping("/{id}/archive")
    public ResponseEntity<Event> archive(@PathVariable Long id) {
        return ResponseEntity.ok(eventService.archiveEvent(id));
    }

    @PutMapping("/{id}/publish")
    public ResponseEntity<Event> publish(@PathVariable Long id) {
        return ResponseEntity.ok(eventService.publishEvent(id));
    }

    @PutMapping("/{id}/dispute")
    public ResponseEntity<Event> dispute(@PathVariable Long id) {
        return ResponseEntity.ok(eventService.disputeEvent(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        eventService.deleteEvent(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{eventId}/location/{locationId}")
    public ResponseEntity<Event> addLocation(@PathVariable Long eventId, @PathVariable Long locationId) {
        return ResponseEntity.ok(eventService.addLocation(eventId, locationId));
    }

    @DeleteMapping("/{eventId}/location/{locationId}")
    public ResponseEntity<Event> removeLocation(@PathVariable Long eventId, @PathVariable Long locationId) {
        return ResponseEntity.ok(eventService.removeLocation(eventId, locationId));
    }

    @GetMapping("/{eventId}/locations")
    public ResponseEntity<Set<Location>> getLocations(@PathVariable Long eventId) {
        return ResponseEntity.ok(eventService.getEventLocations(eventId));
    }

    @GetMapping("/search")
    public ResponseEntity<List<Event>> search(@RequestParam String title,
                                             @RequestParam(required = false) Long locationId,
                                             @RequestParam(required = false) EventStatus status) {
        return ResponseEntity.ok(eventService.searchByMultipleCriteria(title, locationId, status));
    }

    @GetMapping("/stats/total")
    public ResponseEntity<Long> getTotalCount() {
        return ResponseEntity.ok(eventService.getTotalEventCount());
    }

    @GetMapping("/stats/published")
    public ResponseEntity<Long> getPublishedCount() {
        return ResponseEntity.ok(eventService.getPublishedEventCount());
    }

    @GetMapping("/stats/creator/{userId}")
    public ResponseEntity<Long> getCreatorCount(@PathVariable Long userId) {
        return ResponseEntity.ok(eventService.getEventCountByCreator(userId));
    }

    @GetMapping("/stats/location/{locationId}")
    public ResponseEntity<Long> getLocationCount(@PathVariable Long locationId) {
        return ResponseEntity.ok(eventService.getEventCountByLocation(locationId));
    }
}