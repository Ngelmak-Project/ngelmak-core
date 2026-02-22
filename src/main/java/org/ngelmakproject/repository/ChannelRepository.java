package org.ngelmakproject.repository;

import java.util.Optional;

import org.ngelmakproject.domain.Channel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for the Channel entity.
 */
@SuppressWarnings("unused")
@Repository
public interface ChannelRepository extends JpaRepository<Channel, Long> {
    Optional<Channel> findOneByUser(Long id);

    Boolean existsByIdentifier(String identifier);
}
