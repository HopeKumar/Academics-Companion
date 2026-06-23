package com.lms.service;

import com.lms.model.DiscussionReply;
import com.lms.model.DiscussionThread;
import com.lms.repository.DiscussionReplyRepository;
import com.lms.repository.DiscussionThreadRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class DiscussionService {

    private final DiscussionThreadRepository threadRepo;
    private final DiscussionReplyRepository replyRepo;
    private final com.lms.service.ai.AIOrchestratorService aiOrchestratorService;

    @Autowired
    public DiscussionService(DiscussionThreadRepository threadRepo,
                             DiscussionReplyRepository replyRepo,
                             com.lms.service.ai.AIOrchestratorService aiOrchestratorService) {
        this.threadRepo = threadRepo;
        this.replyRepo = replyRepo;
        this.aiOrchestratorService = aiOrchestratorService;
    }

    public DiscussionThread createThread(String title, String content, String authorId, String sourceId) {
        DiscussionThread thread = new DiscussionThread();
        thread.setTitle(title);
        thread.setContent(content);
        thread.setAuthorId(authorId);
        thread.setSourceId(sourceId);
        return threadRepo.save(thread);
    }

    public DiscussionReply createReply(String threadId, String content, String authorId) {
        DiscussionReply reply = new DiscussionReply();
        reply.setThreadId(threadId);
        reply.setContent(content);
        reply.setAuthorId(authorId);
        return replyRepo.save(reply);
    }

    public List<DiscussionThread> getAllThreads() {
        List<DiscussionThread> threads = threadRepo.findAll();
        for (DiscussionThread t : threads) {
            t.setThreadMessages(replyRepo.findByThreadId(t.getId()));
        }
        return threads;
    }

    public DiscussionThread getThreadById(String id) {
        return threadRepo.findById(id).orElse(null);
    }

    public List<DiscussionReply> getReplies(String threadId) {
        return replyRepo.findByThreadId(threadId);
    }

    public DiscussionThread upvoteThread(String threadId) {
        Optional<DiscussionThread> threadOpt = threadRepo.findById(threadId);
        if (threadOpt.isPresent()) {
            DiscussionThread thread = threadOpt.get();
            thread.setUpvotes(thread.getUpvotes() + 1);
            return threadRepo.save(thread);
        }
        return null;
    }

    public DiscussionReply upvoteReply(String replyId) {
        Optional<DiscussionReply> replyOpt = replyRepo.findById(replyId);
        if (replyOpt.isPresent()) {
            DiscussionReply reply = replyOpt.get();
            reply.setUpvotes(reply.getUpvotes() + 1);
            return replyRepo.save(reply);
        }
        return null;
    }

    public DiscussionReply markAcceptedAnswer(String replyId) {
        Optional<DiscussionReply> replyOpt = replyRepo.findById(replyId);
        if (replyOpt.isPresent()) {
            DiscussionReply reply = replyOpt.get();
            reply.setAcceptedAnswer(true);
            return replyRepo.save(reply);
        }
        return null;
    }

    public DiscussionThread generateAISummary(String threadId) {
        Optional<DiscussionThread> threadOpt = threadRepo.findById(threadId);
        if (threadOpt.isEmpty()) return null;

        DiscussionThread thread = threadOpt.get();
        List<DiscussionReply> replies = replyRepo.findByThreadId(threadId);
        
        StringBuilder sb = new StringBuilder();
        sb.append("You are an AI assistant analyzing an academic discussion thread. Extract the following information:\n");
        sb.append("1. A high-level summary of the discussion.\n");
        sb.append("2. Key learning points (bullet points).\n");
        sb.append("3. The accepted solution (if any).\n");
        sb.append("4. Quick revision notes for exam preparation.\n\n");
        sb.append("OUTPUT FORMAT: You MUST respond ONLY with valid JSON in the following format:\n");
        sb.append("{\n");
        sb.append("  \"summary\": \"...\",\n");
        sb.append("  \"keyPoints\": [\"...\"],\n");
        sb.append("  \"acceptedSolution\": \"...\",\n");
        sb.append("  \"revisionNotes\": \"...\"\n");
        sb.append("}\n\n");

        sb.append("Title: ").append(thread.getTitle()).append("\n");
        sb.append("Content: ").append(thread.getContent()).append("\n\n");
        
        for (DiscussionReply reply : replies) {
            sb.append("Reply: ").append(reply.getContent());
            if (reply.isAcceptedAnswer()) {
                sb.append(" (ACCEPTED ANSWER)");
            }
            sb.append("\n");
        }
        
        try {
            String jsonResponse = aiOrchestratorService.generate(sb.toString(), Map.of());
            if (jsonResponse.contains("```json")) {
                jsonResponse = jsonResponse.substring(jsonResponse.indexOf("```json") + 7);
                if (jsonResponse.contains("```")) {
                    jsonResponse = jsonResponse.substring(0, jsonResponse.indexOf("```"));
                }
            } else if (jsonResponse.contains("```")) {
                jsonResponse = jsonResponse.substring(jsonResponse.indexOf("```") + 3);
                if (jsonResponse.contains("```")) {
                    jsonResponse = jsonResponse.substring(0, jsonResponse.indexOf("```"));
                }
            }
            thread.setAiSummary(jsonResponse.trim());
            return threadRepo.save(thread);
        } catch (Exception e) {
            return thread;
        }
    }
}
