package com.lms.repository;

import com.lms.model.BlacklistedToken;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.Date;

public interface BlacklistedTokenRepository extends MongoRepository<BlacklistedToken, String> {
    long deleteByExpiryDateBefore(Date date);
}
