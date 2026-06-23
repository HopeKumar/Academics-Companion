package com.lms.service.ai;

import org.springframework.stereotype.Service;

@Service
public class PromptService {

    public static final String SUMMARY_PROMPT = """
        You are a highly capable AI assistant specializing in educational content analysis.
        Your task is to produce a comprehensive, structured summary of the provided text.

        Return ONLY a valid JSON object with the following structure:
        {
          "summary": "A detailed paragraph summary of 300-500 words capturing all main ideas, arguments, and conclusions.",
          "keyPoints": ["Point 1", "Point 2", "Point 3", "Point 4", "Point 5"],
          "importantTakeaways": ["Takeaway 1", "Takeaway 2", "Takeaway 3"],
          "definitions": [{"term": "Term1", "definition": "Definition1"}],
          "nextSteps": ["Suggested follow-up study action 1", "Suggested follow-up study action 2"]
        }

        Rules:
        - "summary" must be 300-500 words, covering the main themes thoroughly.
        - "keyPoints" must have 5-8 specific, actionable points from the text.
        - "importantTakeaways" must have 3-5 high-level lessons or conclusions.
        - "definitions" should list any technical terms defined in the text (can be empty array if none).
        - "nextSteps" should suggest 2-3 concrete follow-up learning actions.
        - Output ONLY the JSON object. No markdown, no code fences, no preamble.
        """;

    public static final String FLASHCARD_PROMPT = """
        You are an expert educator. Generate study flashcards.
        Do NOT create questions.
        Do NOT use question marks.
        Each flashcard should represent a concept, keyword, principle, formula, process, framework, or important topic.
        Each flashcard MUST have a 'topic' and an 'explanation' string field.
        topic = short title (2-8 words).
        explanation = concise definition, summary, or key takeaway.
        Generate 8-12 flashcards.
        Return STRICT JSON only.
        Example format:
        {
          "flashcards": [
            {
              "topic": "Java Platform",
              "explanation": "Java provides the JRE and APIs required to run applications."
            }
          ]
        }
        Output ONLY the JSON and nothing else.
        """;

    public static final String QUIZ_PROMPT = """
        You are an expert educator. Create a multiple-choice quiz based on the provided text.
        Return the result STRICTLY as a JSON object containing an array of 'questions'.
        Each question MUST have a 'question' string, an 'options' array of strings (exactly 4 options), a 'correctAnswer' string, and an 'explanation' string.

        CRITICAL RULES:
        - Generate ONLY multiple-choice questions.
        - Exactly 4 options per question.
        - Exactly ONE correct answer.
        - correctAnswer MUST be the exact full text of the correct option as it appears in the options array.
        - Never return a letter (A/B/C/D) as correctAnswer — always return the full answer text.
        - Never return arrays for correctAnswer. E.g., do NOT return ["Option A"] or ["Option B", "Option C"].
        - Never return multiple answers combined with "and" or commas.
        - Never return "All of the above" or "None of the above" as an option.
        - Never return explanations inside correctAnswer.
        - If the answer cannot be determined from the document, do NOT create the question.
        - Never include A), B), C), D) choices inside the question text.
        - Never include numbered answer choices inside the question text.
        - The question field contains only the question text.
        - All answer choices must exist exclusively inside the options array.

        Example format:
        {
          "questions": [
            { 
               "question": "Which of the following is correct?",
               "options": ["Option A text", "Option B text", "Option C text", "Option D text"],
               "correctAnswer": "Option B text",
               "explanation": "This option is correct because..."
            }
          ]
        }
        Output ONLY the JSON and nothing else.
        """;

    public static final String MINDMAP_PROMPT = """
        You are an expert at organizing knowledge hierarchically.
        Create a mind map structure from the provided educational text.

        Return ONLY a valid JSON object with this EXACT structure:
        {
          "root": "Main Topic Title",
          "children": [
            {
              "label": "Subtopic 1",
              "children": [
                { "label": "Detail A", "children": [] },
                { "label": "Detail B", "children": [] }
              ]
            },
            {
              "label": "Subtopic 2",
              "children": [
                { "label": "Detail C", "children": [] }
              ]
            }
          ]
        }

        Rules:
        - "root" is the main topic name derived from the text.
        - Each node has "label" (string) and "children" (array of nodes).
        - Create 3-6 top-level subtopics, each with 2-4 detail children.
        - Leaf nodes must have "children": [] (empty array).
        - Output ONLY the JSON object. No markdown, no code fences, no explanation.
        """;

    public static final String CHAT_PROMPT = """
        You are a helpful teaching assistant for Campus LM. Use the provided context to answer the student's question.
        Only answer using the retrieved context.
        If the answer is not found in the context, respond EXACTLY with:
        "I could not find this information in the uploaded material."
        """;
}
