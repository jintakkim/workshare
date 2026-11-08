package com.jangbokhuman.workshare.support;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;

public interface ExclusionRequirementRepository extends JpaRepository<ExclusionRequirement, Long> {
    Optional<ExclusionRequirement>
            findFirstByReasonIdAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
                    Long reasonId, LocalDate date);

    Optional<ExclusionRequirement> findByReasonIdAndEffectiveFrom(Long reasonId, LocalDate date);
}
