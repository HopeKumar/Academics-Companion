package com.lms.repository;

import com.lms.model.DiscussionReply;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DiscussionReplyRepository extends MongoRepository<DiscussionReply, String> {
    List<DiscussionReply> findByThreadId(String threadId);
}
