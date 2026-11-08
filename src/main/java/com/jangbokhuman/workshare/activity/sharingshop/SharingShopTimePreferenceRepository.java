package com.jangbokhuman.workshare.activity.sharingshop;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SharingShopTimePreferenceRepository extends JpaRepository<SharingShopTimePreference, Long> {
    Optional<SharingShopTimePreference> findByEmployeeId(Long employeeId);
}
