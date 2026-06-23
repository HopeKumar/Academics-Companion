package com.lms.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class SlideDeckResponse {

    private String title;
    private List<String> bulletPoints;
    private String speakerNotes;

    public SlideDeckResponse() {
    }

    public SlideDeckResponse(String title, List<String> bulletPoints, String speakerNotes) {
        this.title = title;
        this.bulletPoints = bulletPoints;
        this.speakerNotes = speakerNotes;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public List<String> getBulletPoints() {
        return bulletPoints;
    }

    public void setBulletPoints(List<String> bulletPoints) {
        this.bulletPoints = bulletPoints;
    }

    public String getSpeakerNotes() {
        return speakerNotes;
    }

    public void setSpeakerNotes(String speakerNotes) {
        this.speakerNotes = speakerNotes;
    }
}
