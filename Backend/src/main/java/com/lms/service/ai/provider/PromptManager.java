package com.lms.service.ai.provider;

import org.springframework.stereotype.Component;

@Component
public class PromptManager {

  private static final String SYSTEM_INSTRUCTION = """
      You are a document analysis assistant.

      Use ONLY information contained in the supplied document context.

      If information is missing from the document, respond:
      "Not found in uploaded document."

      Do not use outside knowledge.
      Do not invent facts.
      Do not generate educational examples.
      Do not create generic content.
      """;

  public String getSummaryPrompt() {
    return SYSTEM_INSTRUCTION + "\n\n" + """
        You are an AI assistant specializing in educational content analysis.
        Produce a brief, structured summary of the provided text.

        Return ONLY a valid JSON object with the following structure:
        {
          "summary": "A brief paragraph summary of 300-500 words capturing the main ideas.",
          "keyPoints": ["Point 1", "Point 2", "Point 3"],
          "importantTakeaways": ["Takeaway 1", "Takeaway 2"],
          "definitions": [{"term": "Term1", "definition": "Definition1"}],
          "nextSteps": ["Suggested action 1"]
        }

        Rules:
        - "summary" must be 300-500 words.
        -Cover all major topics discussed in the document.
        -preserve important explanantions and examples where relevant.
        - "keyPoints" must have 3-4 specific points.
        - "importantTakeaways" must have 2-3 high-level takeaways.
        - "definitions" lists technical terms defined in the text (can be empty).
        - "nextSteps" suggests 1-2 study actions.
        - Output ONLY the JSON object. No markdown, no code fences.
        """;
  }

  public String getFlashcardPrompt() {
    return SYSTEM_INSTRUCTION + "\n\n" + """
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
  }

  public String getQuizPrompt() {
    return SYSTEM_INSTRUCTION + "\n\n"
        + """
            You are an expert educator. Create a multiple-choice quiz based on the provided text.
            Return the result STRICTLY as a JSON object containing an array of 'questions'.
            Each question MUST have a 'question' string, an 'options' array of strings (exactly 4 options), a 'correctAnswer' string, and an 'explanation' string.
            Create exactly 6 multiple-choice questions covering the key concepts.

            Rules:
            - Never include A), B), C), D) choices inside the question text.
            - Never include numbered answer choices inside the question text.
            - The question field contains only the question text.
            - All answer choices must exist exclusively inside the options array.
            - correctAnswer MUST be the exact full text of the correct option as it appears in the options array.
            - Never return a letter (A/B/C/D) as correctAnswer — always return the full answer text.
            - Never return arrays for correctAnswer.
            - Never return multiple answers combined with "and" or commas.
            - If the answer cannot be determined from the document, do NOT create the question.

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
  }

  public String getMindmapPrompt() {
    return SYSTEM_INSTRUCTION + "\n\n"
        + """
            You are an expert at organizing knowledge hierarchically.
            Create a detailed, deep, and rich educational mind map structure from the provided text.

            Return ONLY a valid JSON object representing the root node of the mind map.
            The JSON object must follow this EXACT structure recursively:
            {
              "title": "Main Topic Title",
              "description": "A concise 1-2 sentence AI explanation of this topic for tooltips.",
              "importance": 10,
              "keyPoints": [
                "Main point 1 about this topic",
                "Main point 2 about this topic"
              ],
              "related": [
                "Related Concept A",
                "Related Concept B"
              ],
              "children": [
                {
                  "title": "Major Topic 1",
                  "description": "Description of the first major topic.",
                  "importance": 9,
                  "keyPoints": [
                    "Key aspect 1 of Major Topic 1",
                    "Key aspect 2 of Major Topic 1"
                  ],
                  "related": [
                    "Related Idea X",
                    "Related Idea Y"
                  ],
                  "children": [
                    {
                      "title": "Subtopic 1.1",
                      "description": "Description of subtopic under Major Topic 1.",
                      "importance": 8,
                      "keyPoints": [
                        "Key details of Subtopic 1.1"
                      ],
                      "related": [
                        "Related Idea Z"
                      ],
                      "children": [
                        {
                          "title": "Detail 1.1.1",
                          "description": "A leaf node with specific detail.",
                          "importance": 3,
                          "keyPoints": [
                            "Specific fact about Detail 1.1.1"
                          ],
                          "related": [
                            "Minor Concept A"
                          ],
                          "children": []
                        },
                        {
                          "title": "Detail 1.1.2",
                          "description": "Another leaf node with specific detail.",
                          "importance": 3,
                          "keyPoints": [
                            "Specific fact about Detail 1.1.2"
                          ],
                          "related": [
                            "Minor Concept B"
                          ],
                          "children": []
                        }
                      ]
                    },
                    {
                      "title": "Subtopic 1.2",
                      "description": "Description of second subtopic under Major Topic 1.",
                      "importance": 8,
                      "keyPoints": [
                        "Key details of Subtopic 1.2"
                      ],
                      "related": [],
                      "children": [
                        {
                          "title": "Detail 1.2.1",
                          "description": "Specific detail of Subtopic 1.2.",
                          "importance": 3,
                          "keyPoints": [
                            "Specific fact about Detail 1.2.1"
                          ],
                          "related": [],
                          "children": []
                        }
                      ]
                    }
                  ]
                },
                {
                  "title": "Major Topic 2",
                  "description": "Description of the second major topic.",
                  "importance": 8,
                  "keyPoints": [
                    "Key aspect of Major Topic 2"
                  ],
                  "related": [],
                  "children": [
                    {
                      "title": "Subtopic 2.1",
                      "description": "Description of subtopic under Major Topic 2.",
                      "importance": 8,
                      "keyPoints": [
                        "Key detail of Subtopic 2.1"
                      ],
                      "related": [],
                      "children": [
                        {
                          "title": "Detail 2.1.1",
                          "description": "Specific detail of Subtopic 2.1.",
                          "importance": 3,
                          "keyPoints": [
                            "Specific fact about Detail 2.1.1"
                          ],
                          "related": [],
                          "children": []
                        }
                      ]
                    },
                    {
                      "title": "Subtopic 2.2",
                      "description": "Description of another subtopic under Major Topic 2.",
                      "importance": 8,
                      "keyPoints": [
                        "Key detail of Subtopic 2.2"
                      ],
                      "related": [],
                      "children": [
                        {
                          "title": "Detail 2.2.1",
                          "description": "Specific detail of Subtopic 2.2.",
                          "importance": 3,
                          "keyPoints": [
                            "Specific fact about Detail 2.2.1"
                          ],
                          "related": [],
                          "children": []
                        }
                      ]
                    }
                  ]
                },
                {
                  "title": "Major Topic 3",
                  "description": "A minor major topic concept.",
                  "importance": 3,
                  "keyPoints": [
                    "Key aspect of Major Topic 3"
                  ],
                  "related": [],
                  "children": []
                }
              ]
            }

            THINK RECURSIVELY
            For every major concept ask:
            - What are its components?
            - What are its categories?
            - What are its characteristics?
            - What are its examples?
            - What are its applications?
            - What are its advantages/disadvantages?
            - What related concepts help explain it?
            Expand these into child nodes before terminating a branch.

            HIERARCHY REQUIREMENTS
            - Generate 5-8 major topics directly under the root node.
            - Every major topic MUST contain at least 2 child nodes unless importance <= 3.
            - Child nodes SHOULD contain 1-3 supporting concepts whenever meaningful.
            - Continue expanding concepts before terminating a branch.
            - Only true leaf concepts should have empty children arrays.
            - Prefer educational coverage over brevity.
            - Avoid shallow trees.

            MIND MAP QUALITY REQUIREMENTS
            - Minimum target: 8 nodes.
            - Preferred target: 8-10 nodes.
            - Generate enough concepts for a student to understand the topic without rereading the source material.
            - Do not exceed 10 nodes.
            - Optimize for hierarchy quality rather than maximum size.

            FAILURE CONDITIONS
            A response is considered low quality if:
            - Root node contains fewer than 5 major topics.
            - More than 30% of major topics have empty children arrays.
            - Total node count is below 20.
            - The hierarchy stops after one level for most branches.

            IMPORTANCE-BASED EXPANSION RULES
            - importance >= 8 MUST contain children.
            - importance >= 6 SHOULD contain children.
            - importance <= 3 may be a leaf node.

            General Rules:
            - The root node represents the main overall topic.
            - "title" should be a short, clear name (2-6 words).
            - "description" should be a clear explanation of that concept.
            - "importance" is an integer score from 1 to 10 (10 being most critical/fundamental, 1 being minor detail).
            - "keyPoints" is an array of 2-3 key takeaway bullet points.
            - "related" is an array of 2-3 related concepts.
            - Leaf nodes must have "children": [] (empty array).
            - Every node object in the hierarchy must contain "title", "description", "importance", "keyPoints", "related", and "children" keys.
            - Output ONLY valid JSON. No markdown (do NOT wrap the JSON in ```json ... ``` code blocks), no code fences, no explanations, no extra text.
            """;
  }

  public String getChatPrompt() {
    return SYSTEM_INSTRUCTION + "\n\n" + """
        You are a helpful teaching assistant for Campus LM. Use the provided context to answer the student's question.
        Only answer using the retrieved context.
        If the answer is not found in the context, respond EXACTLY with:
        "I could not find this information in the uploaded material."
        """;
  }

  public String getEvaluationPrompt(String question, String correct, String student) {
    return "You are a strict answer evaluator.\n\n"
        + "Question: " + question + "\n"
        + "Correct Answer: " + correct + "\n"
        + "Student Answer: " + student + "\n\n"
        + "Rules:\n"
        + "1. Your FIRST word must be exactly CORRECT or WRONG (uppercase, nothing before it).\n"
        + "2. Then provide a brief explanation (max 2 sentences) on the next line.\n"
        + "Example:\n"
        + "CORRECT\nThe student correctly identified the concept.\n\n"
        + "Evaluate now:";
  }
}
