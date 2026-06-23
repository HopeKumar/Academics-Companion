package com.lms.repository;

import com.lms.model.Podcast;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PodcastRepository extends MongoRepository<Podcast, String> {

    List<Podcast> findByUserIdOrderByCreatedAtDesc(String userId);

    Optional<Podcast> findByUserIdAndSourceId(String userId, String sourceId);

    Page<Podcast> findByUserIdOrderByCreatedAtDesc(String userId, Pageable pageable);

    void deleteBySourceId(String sourceId);
}
