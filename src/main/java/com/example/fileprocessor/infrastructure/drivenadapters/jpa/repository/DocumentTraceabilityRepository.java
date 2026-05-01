package com.example.fileprocessor.infrastructure.drivenadapters.jpa.repository;

import com.example.fileprocessor.infrastructure.drivenadapters.jpa.entity.DocumentTraceabilityEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DocumentTraceabilityRepository extends JpaRepository<DocumentTraceabilityEntity, Long> {
    List<DocumentTraceabilityEntity> findByProductId(String productId);
    List<DocumentTraceabilityEntity> findByStatus(String status);
}
