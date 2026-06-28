package org.ngelmakproject.domain;

import java.io.Serializable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * findByFeedOwner The Feed entity.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "feed")
public class Feed implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "feed_seq")
    @SequenceGenerator(name = "feed_seq", sequenceName = "feed_seq", allocationSize = 50)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "post_id", nullable = false, foreignKey = @ForeignKey(name = "fk_feed_post", foreignKeyDefinition = "FOREIGN KEY (post_id) REFERENCES post(id) ON DELETE CASCADE"))
    private Post post;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "feed_owner_id", nullable = false, foreignKey = @ForeignKey(name = "fk_feed_channel", foreignKeyDefinition = "FOREIGN KEY (feed_owner_id) REFERENCES channel(id) ON DELETE CASCADE"))
    private Channel feedOwner;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Post getPost() {
        return post;
    }

    public void setPost(Post post) {
        this.post = post;
    }

    public Channel getFeedOwner() {
        return feedOwner;
    }

    public void setFeedOwner(Channel feedOwner) {
        this.feedOwner = feedOwner;
    }

}
