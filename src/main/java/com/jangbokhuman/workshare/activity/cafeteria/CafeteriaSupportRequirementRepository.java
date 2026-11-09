package com.jangbokhuman.workshare.activity.cafeteria;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface CafeteriaSupportRequirementRepository extends JpaRepository<CafeteriaSupportRequirement, Long> {
    Optional<CafeteriaSupportRequirement> findFirstByEffectiveFromLessThanEqualOrderByEffectiveFromDesc(LocalDate date);

    Optional<CafeteriaSupportRequirement> findByEffectiveFrom(LocalDate date);

    List<CafeteriaSupportRequirement> findAllByOrderByEffectiveFromAsc();
}
