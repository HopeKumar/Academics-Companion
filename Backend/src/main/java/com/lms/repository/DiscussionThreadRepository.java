package com.lms.repository;

import com.lms.model.DiscussionThread;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DiscussionThreadRepository extends MongoRepository<DiscussionThread, String> {
}
