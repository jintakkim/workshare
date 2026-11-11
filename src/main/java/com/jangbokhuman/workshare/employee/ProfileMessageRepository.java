package com.jangbokhuman.workshare.employee;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProfileMessageRepository extends JpaRepository<ProfileMessage, Long> {
    List<ProfileMessage> findByRecipientIdOrderByCreatedAtDescIdDesc(Long recipientId);

    Optional<ProfileMessage> findByIdAndRecipientId(Long id, Long recipientId);
}
