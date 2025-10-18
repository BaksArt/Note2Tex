package com.baksart.Note2TexBack.project;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProjectRepo extends JpaRepository<Project, Long> {
    List<Project> findAllByOwner_IdOrderByCreatedAtDesc(Long ownerId);
    Optional<Project> findByIdAndOwner_Id(Long id, Long ownerId);
}
