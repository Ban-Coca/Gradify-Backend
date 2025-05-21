package com.capstone.gradify.Service.AiServices;

import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.capstone.gradify.Entity.records.ClassSpreadsheet;
import com.capstone.gradify.Entity.records.GradeRecordsEntity;
import com.capstone.gradify.Entity.records.GradingSchemes;
import com.capstone.gradify.Service.GradingSchemeService;
import com.capstone.gradify.Service.RecordsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;

import java.util.List;
import java.util.Map;

@Service
public class GenerateFeedbackAIService {
    @Autowired
    private RecordsService recordsService;
    @Autowired
    private GradingSchemeService gradingSchemeService;
    AnthropicClient client = AnthropicOkHttpClient.fromEnv();

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
        String formattedPrompt = String.format(
                "Student Information:\n" +
                        "Student ID: %s\n" +
                        "Class: %s\n\n" +
                        "Grades:\n%s\n\n" +
                        "Assessment Maximum Values:\n%s\n\n" +
                        "Grading Scheme:\n%s\n\n" +
                        "Overall Grade: %.2f%%\n\n" +
                        "Please generate personalized feedback for this student based on their performance.",
                record.getStudentNumber(),
                classSpreadsheet.getClassName(),
                formatMap(grades),
                formatMap(maxValues),
                gradingScheme.getGradingScheme(),
                overallGrade
        );

        String systemPrompt = "You are an educational assistant. Generate clear, concise, and constructive feedback or reports based on the provided student data or assignment. Focus on actionable suggestions, highlight strengths and areas for improvement, and maintain a professional, supportive tone. Ensure all information is accurate and relevant to the context.  Be more human on the feedback, make it a letter format. Tone should be casual but professional. PASSING percentage is 60%";

        MessageCreateParams params = MessageCreateParams.builder()
                .model("claude-3-5-haiku-20241022")
                .system(systemPrompt)
                .addUserMessage(formattedPrompt)
                .maxTokens(8192)
                .temperature(1)
                .build();

        Message response = client.messages().create(params);
        return response.content().get(0).text().toString();
    }

    private String formatMap(Map<?, ?> map) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            sb.append("- ").append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
        }
        return sb.toString();
    }
}
