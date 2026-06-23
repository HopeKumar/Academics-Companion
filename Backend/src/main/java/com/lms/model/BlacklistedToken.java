package com.lms.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import java.util.Date;

@Document(collection = "blacklisted_tokens")
public class BlacklistedToken {

    @Id
    private String id; // The JWT JTI (unique ID)

    @Indexed(expireAfterSeconds = 0)
    private Date expiryDate;

    public BlacklistedToken() {}

    public BlacklistedToken(String jti, Date expiryDate) {
        this.id = jti;
        this.expiryDate = expiryDate;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Date getExpiryDate() {
        return expiryDate;
    }

    public void setExpiryDate(Date expiryDate) {
        this.expiryDate = expiryDate;
    }
}
