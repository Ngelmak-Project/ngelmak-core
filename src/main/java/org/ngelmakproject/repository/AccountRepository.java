package org.ngelmakproject.repository;

import java.util.Optional;

import org.ngelmakproject.domain.Account;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for the Account entity.
 */
@SuppressWarnings("unused")
@Repository
public interface AccountRepository extends JpaRepository<Account, Long> {

    @EntityGraph(attributePaths = { "configuration" })
    Optional<Account> findOneByUser(Long id);

    Boolean existsByIdentifier(String identifier);

}
