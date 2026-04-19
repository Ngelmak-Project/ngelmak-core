// package org.ngelmakproject.service;

// import java.time.Instant;
// import java.util.List;
// import java.util.Set;
// import java.util.stream.Collectors;

// import org.ngelmakproject.domain.Event;
// import org.ngelmakproject.domain.Event.EventStatus;
// import org.ngelmakproject.domain.File;
// import org.ngelmakproject.domain.Location;
// import org.ngelmakproject.domain.Post;
// import org.ngelmakproject.repository.EventRepository;
// import org.ngelmakproject.repository.LocationRepository;
// import org.ngelmakproject.web.rest.errors.ResourceNotFoundException;
// import org.springframework.beans.factory.annotation.Autowired;
// import org.springframework.stereotype.Service;
// import org.springframework.web.multipart.MultipartFile;

// import jakarta.transaction.Transactional;

// @Service
// @Transactional
// public class EventService {

//     @Autowired
//     private EventRepository eventRepository;

//     @Autowired
//     private PostService postService;

//     @Autowired
//     private LocationRepository locationRepository;

//     // CREATE
//     public Event createEvent(Event dto, List<MultipartFile> medias, List<MultipartFile> covers) {
//         Post post = postService.save(dto.getPost(), medias, covers);

//         Event event = new Event();
//         event.setTitle(dto.getTitle());
//         event.setPost(post);
//         event.setCreatedByUser(dto.getCreatedByUser());
//         event.setStatus(EventStatus.PUBLISHED);

//         Set<Location> locations = locationRepository
//                 .findByIdIn(dto.getLocations().stream().map(Location::getId).collect(Collectors.toList()));
//         event.setLocations(locations);

//         return eventRepository.save(event);
//     }

//     // READ
//     public Event getEventById(Long id) {
//         return eventRepository.findById(id)
//                 .orElseThrow(() -> new ResourceNotFoundException("Event not found", "event", "notFound"));
//     }

//     public List<Event> getAllEvents() {
//         return eventRepository.findAll();
//     }

//     public List<Event> getEventsByStatus(EventStatus status) {
//         return eventRepository.findByStatus(status);
//     }

//     public List<Event> getEventsByLocation(Long locationId) {
//         return eventRepository.findByLocationsId(locationId);
//     }

//     public List<Event> getEventsByCreator(Long userId) {
//         return eventRepository.findByCreatedByUser(userId);
//     }

//     public List<Event> getPublishedEvents() {
//         return eventRepository.findByStatus(EventStatus.PUBLISHED);
//     }

//     public List<Event> getRecentEvents(int limit) {
//         return eventRepository.findAllByOrderByCreatedAtDesc().stream().limit(limit).collect(Collectors.toList());
//     }

//     // UPDATE
//     public Event updateEvent(Long id, Event dto, List<MultipartFile> medias,
//             List<File> deletedMedias, List<MultipartFile> covers) {
//         Event event = getEventById(id);

//         event.setTitle(dto.getTitle());
//         event.getPost().setContent(dto.getPost().getContent());

//         postService.update(event.getPost(), deletedMedias, medias, covers);

//         if (!dto.getLocations().isEmpty()) {
//             Set<Location> locations = locationRepository
//                     .findByIdIn(dto.getLocations().stream().map(Location::getId).collect(Collectors.toList()));
//             event.setLocations(locations);
//         }

//         return eventRepository.save(event);
//     }

//     public Event updateEventStatus(Long id, EventStatus status) {
//         Event event = getEventById(id);
//         event.setStatus(status);
//         return eventRepository.save(event);
//     }

//     public Event archiveEvent(Long id) {
//         return updateEventStatus(id, EventStatus.ARCHIVED);
//     }

//     public Event publishEvent(Long id) {
//         return updateEventStatus(id, EventStatus.PUBLISHED);
//     }

//     public Event disputeEvent(Long id) {
//         return updateEventStatus(id, EventStatus.DISPUTED);
//     }

//     // DELETE
//     public void deleteEvent(Long id) {
//         Event event = getEventById(id);
//         postService.delete(event.getPost().getId());
//         eventRepository.deleteById(id);
//     }

//     // LOCATION MANAGEMENT
//     public Event addLocation(Long eventId, Long locationId) {
//         Event event = getEventById(eventId);
//         Location location = locationRepository.findById(locationId)
//                 .orElseThrow(() -> new ResourceNotFoundException("Location not found", "location", "notFound"));
//         event.getLocations().add(location);
//         return eventRepository.save(event);
//     }

//     public Event removeLocation(Long eventId, Long locationId) {
//         Event event = getEventById(eventId);
//         event.getLocations().removeIf(loc -> loc.getId().equals(locationId));
//         return eventRepository.save(event);
//     }

//     public Set<Location> getEventLocations(Long eventId) {
//         return getEventById(eventId).getLocations();
//     }

//     // SEARCH
//     public List<Event> searchByTitle(String keyword) {
//         return eventRepository.findByTitleContainingIgnoreCase(keyword);
//     }

//     public List<Event> searchByMultipleCriteria(String title, Long locationId, EventStatus status) {
//         return eventRepository.findByTitleContainingIgnoreCaseAndLocationsIdAndStatus(title, locationId, status);
//     }

//     // FILTERING
//     public List<Event> getEventsByDateRange(Instant startDate, Instant endDate) {
//         return eventRepository.findByCreatedAtBetween(startDate, endDate);
//     }

//     public List<Event> getEventsSortedByCreatedDate() {
//         return eventRepository.findAllByOrderByCreatedAtDesc();
//     }

//     // BATCH OPERATIONS
//     public List<Event> bulkArchiveEvents(List<Long> eventIds) {
//         List<Event> events = eventRepository.findAllById(eventIds);
//         events.forEach(e -> e.setStatus(EventStatus.ARCHIVED));
//         return eventRepository.saveAll(events);
//     }

//     public void bulkDeleteEvents(List<Long> eventIds) {
//         eventRepository.deleteAllById(eventIds);
//     }

//     // STATISTICS
//     public long getTotalEventCount() {
//         return eventRepository.count();
//     }

//     public long getPublishedEventCount() {
//         return eventRepository.countByStatus(EventStatus.PUBLISHED);
//     }

//     public long getEventCountByCreator(Long userId) {
//         return eventRepository.countByCreatedByUser(userId);
//     }

//     public long getEventCountByLocation(Long locationId) {
//         return eventRepository.countByLocationsId(locationId);
//     }

//     // PERMISSIONS CHECK
//     public boolean canEditEvent(Long eventId, Long userId) {
//         Event event = getEventById(eventId);
//         return event.getCreatedByUser().equals(userId);
//     }

//     public boolean canDeleteEvent(Long eventId, Long userId) {
//         return canEditEvent(eventId, userId);
//     }
// }
