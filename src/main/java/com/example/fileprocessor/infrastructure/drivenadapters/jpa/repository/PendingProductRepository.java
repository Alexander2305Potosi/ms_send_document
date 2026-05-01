package com.example.fileprocessor.infrastructure.drivenadapters.jpa.repository;

import com.example.fileprocessor.infrastructure.drivenadapters.jpa.entity.PendingProductEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PendingProductRepository extends JpaRepository<PendingProductEntity, String> {
}
