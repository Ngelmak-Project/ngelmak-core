// package org.ngelmakproject.domain;

// import java.time.Instant;
// import java.util.HashSet;
// import java.util.Set;

// import org.hibernate.annotations.CreationTimestamp;
// import org.hibernate.annotations.UpdateTimestamp;

// import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

// import jakarta.persistence.Column;
// import jakarta.persistence.Entity;
// import jakarta.persistence.EnumType;
// import jakarta.persistence.Enumerated;
// import jakarta.persistence.GeneratedValue;
// import jakarta.persistence.GenerationType;
// import jakarta.persistence.Id;
// import jakarta.persistence.JoinColumn;
// import jakarta.persistence.JoinTable;
// import jakarta.persistence.ManyToMany;
// import jakarta.persistence.OneToOne;
// import jakarta.persistence.SequenceGenerator;
// import jakarta.persistence.Table;

// /**
//  * An Event.
//  */
// @Entity
// @Table(name = "event")
// @JsonIgnoreProperties(ignoreUnknown = true)
// public class Event {

// @Id
// @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "event_seq")
// @SequenceGenerator(name = "event_seq", sequenceName = "event_seq", allocationSize = 50)
// @Column(name = "id")
// private Long id;


//     @Column(name = "title", nullable = false, length = 255)
//     private String title;

//     @OneToOne
//     @JoinColumn(name = "post_id", nullable = false)
//     private Post post; // Link to your existing Post

//     @ManyToMany
//     @JoinTable(name = "event_locations", joinColumns = @JoinColumn(name = "event_id"), inverseJoinColumns = @JoinColumn(name = "location_id"))
//     private Set<Location> locations = new HashSet<>();

//     @Column(name = "event_stattus", length = 20, nullable = false)
//     @Enumerated(EnumType.STRING)
//     private EventStatus status; // PUBLISHED, ARCHIVED, DISPUTED

//     @Column(name = "created_by_user_id", nullable = false)
//     private Long createdByUser; // User ID of the author (from auth-service)

//     @CreationTimestamp
//     @Column(name = "created_at", updatable = false)
//     private Instant createdAt;

//     @UpdateTimestamp
//     @Column(name = "updated_at")
//     private Instant updatedAt;

//     public enum EventStatus {
//         PUBLISHED,
//         ARCHIVED,
//         DISPUTED
//     }

//     public Long getId() {
//         return id;
//     }

//     public void setId(Long id) {
//         this.id = id;
//     }

//     public String getTitle() {
//         return title;
//     }

//     public void setTitle(String title) {
//         this.title = title;
//     }

//     public Post getPost() {
//         return post;
//     }

//     public void setPost(Post post) {
//         this.post = post;
//     }

//     public Set<Location> getLocations() {
//         return locations;
//     }

//     public void setLocations(Set<Location> locations) {
//         this.locations = locations;
//     }

//     public EventStatus getStatus() {
//         return status;
//     }

//     public void setStatus(EventStatus status) {
//         this.status = status;
//     }

//     public Long getCreatedByUser() {
//         return createdByUser;
//     }

//     public void setCreatedByUser(Long createdByUser) {
//         this.createdByUser = createdByUser;
//     }

//     public Instant getCreatedAt() {
//         return createdAt;
//     }

//     public void setCreatedAt(Instant createdAt) {
//         this.createdAt = createdAt;
//     }

//     public Instant getUpdatedAt() {
//         return updatedAt;
//     }

//     public void setUpdatedAt(Instant updatedAt) {
//         this.updatedAt = updatedAt;
//     }

//     @Override
//     public String toString() {
//         return "Event [id=" + id + ", title=" + title + ", post=" + post + ", locations=" + locations + ", status="
//                 + status + ", createdByUser=" + createdByUser + ", createdAt=" + createdAt + ", updatedAt=" + updatedAt
//                 + "]";
//     }
// }
