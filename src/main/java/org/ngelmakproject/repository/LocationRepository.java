package org.ngelmakproject.repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.ngelmakproject.domain.Location;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LocationRepository extends JpaRepository<Location, Long> {

    List<Location> findByType(String type);

    List<Location> findByNameContainingIgnoreCase(String name);

    List<Location> findByCodeContainingIgnoreCase(String code);

    List<Location> findByNameContainingIgnoreCaseOrCodeContainingIgnoreCase(String name, String code);

    List<Location> findByNameContainingIgnoreCaseAndType(String name, String type);

    List<Location> findByParentLocationId(Long parentLocationId);

    List<Location> findByParentLocationIdIsNull();

    Optional<Location> findByCodeIgnoreCase(String code);

    List<Location> findAllByOrderByNameAsc();

    List<Location> findAllByOrderByTypeAsc();

    long countByType(String type);

    long countByParentLocationId(Long parentLocationId);

    boolean existsByCodeIgnoreCase(String code);

    Set<Location> findByIdIn(List<Long> ids);
}
