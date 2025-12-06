package com.capstone.gradify.Service.AiServices;

import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.TextBlock;
import com.capstone.gradify.Entity.records.ClassSpreadsheet;
import com.capstone.gradify.Entity.records.GradeRecordsEntity;
import com.capstone.gradify.Entity.records.GradingSchemes;
import com.capstone.gradify.Service.academic.GradingSchemeService;
import com.capstone.gradify.Service.academic.RecordsService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class GenerateFeedbackAIService {

    private final RecordsService recordsService;
    private final GradingSchemeService gradingSchemeService;

    @Value("${anthropic.api.key}")
    private String anthropicApiKey;

    private AnthropicClient client;

    @PostConstruct
    public void init() {
        // Initialize the Anthropic client with the API key
        client = new AnthropicOkHttpClient.Builder()
                .apiKey(anthropicApiKey)
                .build();
    }

    public String generateFeedbackAI(int studentId, int classId) {
        // Fetch required data
        List<GradeRecordsEntity> studentRecords = recordsService.getGradeRecordsByStudentIdAndClassId(studentId, classId);
        if (studentRecords.isEmpty()) {
            return "No records found for this student in this class";
        }

        GradeRecordsEntity record = studentRecords.get(0);
        Map<String, String> grades = record.getGrades();

        // Get class details
        ClassSpreadsheet classSpreadsheet = record.getClassRecord();
        Map<String, Integer> maxValues = classSpreadsheet.getAssessmentMaxValues();

        // Get grading scheme
        GradingSchemes gradingScheme = gradingSchemeService.getGradingSchemeByClassEntityId(classId);

        // Calculate overall grade
        double overallGrade = recordsService.calculateGrade(grades, gradingScheme.getGradingScheme(), maxValues);

        // Format the prompt with all data
        String formattedPrompt = String.format("""
                        Student Performance Data
                        ------------------------
                        Student ID: %s
                        Class: %s
                        
                        Grades by Assessment:
                        %s
                        
                        Assessment Maximum Scores:
                        %s
                        
                        Grading Scheme:
                        %s
                        
                        Calculated Overall Grade: %.2f%%
                        
                        Instructions:
                        Based on the information above, write personalized feedback addressed to the student.
                        
                        The feedback should:
                        - Begin with an **overview** of their performance level in this class.
                        - Highlight their **strongest areas or improvements**.
                        - Discuss **specific areas where they can improve**.
                        - Provide **clear, actionable advice** to help them make progress.
                        - Use the **60%% passing mark** to contextualize their results (e.g., "You are performing above the passing mark, but...").
                        - Maintain a **professional, supportive tone** that motivates growth and reflection.
                        """,
                record.getStudentNumber(),
                classSpreadsheet.getClassName(),
                formatMap(grades),
                formatMap(maxValues),
                gradingScheme.getGradingScheme(),
                overallGrade
        );


        String systemPrompt = """
                You are an educational feedback assistant. Generate personalized, constructive feedback for students based on their performance data.
                
                Guidelines:
                - Address the feedback **directly to the student**.
                - Maintain a **professional, supportive, and clear tone** — not overly formal, but not casual either.
                - Focus on performance interpretation and growth, not just praise or criticism.
                - Organize feedback into clear sections or paragraphs:
                  1. **Overview** – Brief summary of the student's performance.
                  2. **Strengths** – Highlight what they did well.
                  3. **Areas for Improvement** – Point out specific gaps or inconsistencies.
                  4. **Suggestions** – Offer practical, actionable advice for improvement.
                - Avoid restating raw scores or repeating data from the input.
                - Use the **60% passing mark** as a reference point for interpreting results.
                - Keep the length concise (around 3–5 short paragraphs).
                """;


        MessageCreateParams params = MessageCreateParams.builder()
                .model("claude-3-5-haiku-20241022")
                .system(systemPrompt)
                .addUserMessage(formattedPrompt)
                .maxTokens(8192)
                .temperature(1)
                .build();

        Message response = client.messages().create(params);
        return response.content().get(0).text()
                .map(TextBlock::text)
                .orElse("No feedback available");
    }

    private String formatMap(Map<?, ?> map) {
        if (map == null) {
            return "No data available";
        }

        StringBuilder sb = new StringBuilder();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            sb.append("- ").append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
        }
        return sb.toString();
    }
}
