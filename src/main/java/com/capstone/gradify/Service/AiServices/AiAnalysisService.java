package com.capstone.gradify.Service.AiServices;

import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.capstone.gradify.Entity.records.ClassEntity;
import com.capstone.gradify.Entity.records.ClassSpreadsheet;
import com.capstone.gradify.Entity.records.GradeRecordsEntity;
import com.capstone.gradify.Entity.records.GradingSchemes;
import com.capstone.gradify.Repository.records.ClassSpreadsheetRepository;
import com.capstone.gradify.Service.GradingSchemeService;
import com.capstone.gradify.Service.RecordsService;
import com.capstone.gradify.Service.ClassService;
import com.capstone.gradify.Service.spreadsheet.ClassSpreadsheetService;
import org.springframework.stereotype.Service;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.TextBlock;
import com.anthropic.client.AnthropicClient;

import java.util.*;

@Service
public class AiAnalysisService {

    @Value("${anthropic.api.key}")
    private String anthropicApiKey;

    @Autowired
    private ClassService classService;

    @Autowired
    private ClassSpreadsheetService classSpreadsheetService;

    @Autowired
    private RecordsService recordsService;

    @Autowired
    private GradingSchemeService gradingSchemeService;
    @Autowired
    private ClassSpreadsheetRepository classSpreadsheetRepository;
    private AnthropicClient client;

    @PostConstruct
    public void init(){
        // Initialize the Anthropic client with the API key
        client = new AnthropicOkHttpClient.Builder()
                .apiKey(anthropicApiKey)
                .build();
    }

    public String analyzeClass(int classId) {
        try {
            // Get class details
            ClassEntity classEntity = classService.getClassById(classId);

            // Get class spreadsheet
            List<ClassSpreadsheet> classSpreadsheet = classSpreadsheetRepository.findByClassEntity_ClassId(classId);

            if (classSpreadsheet == null) {
                return "No spreadsheet data available for this class.";
            }

            // Get all grade records for the class
            List<GradeRecordsEntity> gradeRecords = classSpreadsheet.get(0).getGradeRecords();
            if (gradeRecords.isEmpty()) {
                return "No grade records available for analysis.";
            }

            // Get grading scheme
            GradingSchemes gradingScheme = gradingSchemeService.getGradingSchemeByClassEntityId(classId);

            // Calculate performance metrics
            Map<String, Object> analysisData = calculatePerformanceMetrics(gradeRecords, classSpreadsheet.get(0), gradingScheme);

            // Format the data for AI analysis
            String formattedPrompt = formatDataForAnalysis(classEntity, classSpreadsheet.get(0), gradeRecords, gradingScheme, analysisData);

            // System prompt for the AI
            String systemPrompt = "You are an expert educational data analyst providing actionable, evidence-based insights to help teachers improve student outcomes. Analyze academic data (scores, participation, attendance, engagement metrics) while maintaining strict privacy standards and pedagogical best practices.\n" +
                    "Required Analysis Structure\n" +
                    "1. Performance Overview\n" +
                    "\n" +
                    "Class statistics (mean, median, standard deviation, grade distribution)\n" +
                    "Achievement against learning objectives\n" +
                    "Temporal trends and trajectory analysis\n" +
                    "\n" +
                    "2. Curriculum Assessment\n" +
                    "\n" +
                    "Strong performance areas (>75% proficiency)\n" +
                    "Weakness areas (<60% proficiency)\n" +
                    "Assignment difficulty patterns and optimal timing\n" +
                    "\n" +
                    "3. Student Risk Identification\n" +
                    "\n" +
                    "At-risk students (use Student #X format only)\n" +
                    "Risk categories: academic struggle, disengagement, attendance\n" +
                    "Early warning indicators from multiple data points\n" +
                    "\n" +
                    "4. Predictive Insights\n" +
                    "\n" +
                    "Projected end-of-term outcomes based on current trajectory\n" +
                    "Students likely to benefit from targeted intervention\n" +
                    "Critical intervention windows with confidence levels\n" +
                    "\n" +
                    "5. Actionable Recommendations\n" +
                    "Immediate (1-2 weeks): Most urgent interventions\n" +
                    "Short-term (1 month): Instructional adjustments and student support\n" +
                    "Long-term (term remainder): Curriculum and assessment modifications\n" +
                    "Include specific strategies for differentiated instruction, parent communication priorities, and professional development needs.\n" +
                    "Standards & Guidelines\n" +
                    "Statistical Rigor: Use appropriate measures, state confidence levels, acknowledge limitations\n" +
                    "Privacy: Anonymous identifiers only, no stigmatizing language, focus on growth potential\n" +
                    "Evidence-Based: Distinguish correlation from causation, provide context for findings\n" +
                    "Actionable: Specific, measurable recommendations with clear timelines\n" +
                    "Output Format\n" +
                    "\n" +
                    "Clear headings with bullet points for key findings\n" +
                    "Prioritize high-impact insights first\n" +
                    "Include data visualization descriptions when relevant\n" +
                    "End with top 3-5 action items summary\n" +
                    "Return your analysis as clean, well-structured HTML using Tailwind CSS classes:\\n\" +\n" +
                    "    \"- Use Tailwind utility classes for all styling\\n\" +\n" +
                    "    \"- Structure: <div class='max-w-4xl mx-auto p-6 bg-white'> as main container\\n\" +\n" +
                    "    \"- Headers: <h2 class='text-2xl font-bold text-gray-800 mb-4 border-b-2 border-blue-500 pb-2'>\\n\" +\n" +
                    "    \"- Subheaders: <h3 class='text-xl font-semibold text-gray-700 mb-3 mt-6'>\\n\" +\n" +
                    "    \"- Sections: <div class='mb-8 bg-gray-50 p-4 rounded-lg'>\\n\" +\n" +
                    "    \"- Lists: <ul class='list-disc pl-6 space-y-2 text-gray-700'>\\n\" +\n" +
                    "    \"- Important metrics: <span class='bg-blue-100 text-blue-800 px-2 py-1 rounded font-medium'>\\n\" +\n" +
                    "    \"- Risk indicators: <span class='bg-red-100 text-red-800 px-2 py-1 rounded font-medium'>\\n\" +\n" +
                    "    \"- Positive indicators: <span class='bg-green-100 text-green-800 px-2 py-1 rounded font-medium'>\\n\" +\n" +
                    "    \"- Tables: <table class='w-full border-collapse border border-gray-300'> with <th class='bg-gray-100 border border-gray-300 px-4 py-2 text-left font-semibold'>\\n\" +\n" +
                    "    \"- Action items: <div class='bg-yellow-50 border-l-4 border-yellow-400 p-4 mb-4'>\\n\" +\n" +
                    "    \"- No <html>, <head>, or <body> tags - just the content HTML with Tailwind classes\\n\"";

            // Call Claude API for analysis
            MessageCreateParams params = MessageCreateParams.builder()
                    .model("claude-3-5-haiku-20241022")
                    .system(systemPrompt)
                    .addUserMessage(formattedPrompt)
                    .maxTokens(8192)
                    .temperature(0.3)
                    .build();

            Message response = client.messages().create(params);
            return response.content().get(0).text()
                    .map(TextBlock::text)
                    .orElse("Analysis could not be generated");

        } catch (Exception e) {
            return "Error performing class analysis: " + e.getMessage();
        }
    }

    private Map<String, Object> calculatePerformanceMetrics(List<GradeRecordsEntity> gradeRecords,
                                                            ClassSpreadsheet classSpreadsheet,
                                                            GradingSchemes gradingScheme) {
        Map<String, Object> metrics = new HashMap<>();
        int classId = classSpreadsheet.getClassEntity().getClassId();
        Map<String, Integer> maxValues = classSpreadsheet.getAssessmentMaxValues();

        // Use RecordsService to calculate the class average
        double overallAverage = recordsService.calculateClassAverageGrade(classId);

        // Calculate grade distribution using the same logic as RecordsService
        Map<String, Integer> gradeDistribution = new HashMap<>();
        gradeDistribution.put("A", 0);
        gradeDistribution.put("B", 0);
        gradeDistribution.put("C", 0);
        gradeDistribution.put("D", 0);
        gradeDistribution.put("F", 0);

        // Use RecordsService's grade calculation for consistency
        for (GradeRecordsEntity record : gradeRecords) {
            double grade = recordsService.calculateGrade(
                    record.getGrades(),
                    gradingScheme.getGradingScheme(),
                    maxValues
            );
            // Convert grade to percentage
            double percentage = grade/100;
            // Use the same grade boundaries as RecordsService
            String letterGrade = getLetterGrade(percentage);
            gradeDistribution.put(letterGrade, gradeDistribution.get(letterGrade) + 1);
        }

        // Calculate assessment averages
        Map<String, Double> assessmentAverages = calculateAssessmentAverages(gradeRecords, maxValues);

        // Gather metrics
        metrics.put("totalStudents", gradeRecords.size());
        metrics.put("assessmentAverages", assessmentAverages);
        metrics.put("overallAverage", overallAverage);
        metrics.put("gradeDistribution", gradeDistribution);

        return metrics;
    }

    private String getLetterGrade(double percentage) {
        if (percentage >= 90) return "A";
        else if (percentage >= 80) return "B";
        else if (percentage >= 70) return "C";
        else if (percentage >= 60) return "D";
        else return "F";
    }

    private Map<String, Double> calculateAssessmentAverages(List<GradeRecordsEntity> gradeRecords,
                                                            Map<String, Integer> maxValues) {
        Map<String, List<Double>> assessmentScores = new HashMap<>();
        Map<String, Double> assessmentAverages = new HashMap<>();

        // Initialize collections for all assessments
        for (String assessment : maxValues.keySet()) {
            assessmentScores.put(assessment, new ArrayList<>());
        }

        // Collect scores for each assessment
        for (GradeRecordsEntity record : gradeRecords) {
            Map<String, String> grades = record.getGrades();
            for (Map.Entry<String, String> entry : grades.entrySet()) {
                String assessment = entry.getKey();
                String score = entry.getValue();
                if (score != null && !score.trim().isEmpty() && maxValues.containsKey(assessment)) {
                    try {
                        double numericScore = Double.parseDouble(score);
                        assessmentScores.get(assessment).add(numericScore);
                    } catch (NumberFormatException ignored) {
                        // Skip non-numeric grades
                    }
                }
            }
        }

        // Calculate averages
        for (Map.Entry<String, List<Double>> entry : assessmentScores.entrySet()) {
            String assessment = entry.getKey();
            List<Double> scores = entry.getValue();
            if (!scores.isEmpty()) {
                double average = scores.stream().mapToDouble(Double::doubleValue).average().orElse(0);
                assessmentAverages.put(assessment, average);
            } else {
                assessmentAverages.put(assessment, 0.0);
            }
        }

        return assessmentAverages;
    }

    private String formatDataForAnalysis(ClassEntity classEntity,
                                         ClassSpreadsheet classSpreadsheet,
                                         List<GradeRecordsEntity> gradeRecords,
                                         GradingSchemes gradingScheme,
                                         Map<String, Object> metrics) {
        StringBuilder sb = new StringBuilder();

        // Basic class information
        sb.append("CLASS INFORMATION:\n");
        sb.append("Class Name: ").append(classEntity.getClassName()).append("\n");
        sb.append("Section: ").append(classEntity.getSection()).append("\n");
        sb.append("Semester: ").append(classEntity.getSemester()).append("\n");
        sb.append("School Year: ").append(classEntity.getSchoolYear()).append("\n");
        sb.append("Total Students: ").append(metrics.get("totalStudents")).append("\n\n");

        // Assessment structure
        sb.append("ASSESSMENT STRUCTURE:\n");
        Map<String, Integer> maxValues = classSpreadsheet.getAssessmentMaxValues();
        for (Map.Entry<String, Integer> entry : maxValues.entrySet()) {
            sb.append("- ").append(entry.getKey()).append(": ")
                    .append(entry.getValue()).append(" points\n");
        }
        sb.append("\n");

        // Grading scheme
        sb.append("GRADING SCHEME:\n");
        sb.append(gradingScheme.getGradingScheme()).append("\n\n");

        // Performance metrics
        sb.append("PERFORMANCE METRICS:\n");
        sb.append("Overall Class Average: ").append(metrics.get("overallAverage")).append("%\n\n");

        // Assessment averages
        sb.append("ASSESSMENT AVERAGES:\n");
        @SuppressWarnings("unchecked")
        Map<String, Double> assessmentAverages = (Map<String, Double>) metrics.get("assessmentAverages");
        for (Map.Entry<String, Double> entry : assessmentAverages.entrySet()) {
            sb.append("- ").append(entry.getKey()).append(": ")
                    .append(String.format("%.2f", entry.getValue())).append(" / ")
                    .append(maxValues.get(entry.getKey()))
                    .append(" (").append(String.format("%.2f",
                            entry.getValue() / maxValues.get(entry.getKey()) * 100)).append("%)\n");
        }
        sb.append("\n");

        // Grade distribution
        sb.append("GRADE DISTRIBUTION:\n");
        @SuppressWarnings("unchecked")
        Map<String, Integer> distribution = (Map<String, Integer>) metrics.get("gradeDistribution");
        for (Map.Entry<String, Integer> entry : distribution.entrySet()) {
            sb.append(entry.getKey()).append(": ").append(entry.getValue()).append(" students\n");
        }
        sb.append("\n");

        // Student data (anonymized by student number)
        sb.append("INDIVIDUAL STUDENT PERFORMANCE:\n");
        for (GradeRecordsEntity record : gradeRecords) {
            sb.append("Student #").append(record.getStudentNumber()).append(":\n");
            double grade = recordsService.calculateGrade(
                    record.getGrades(),
                    gradingScheme.getGradingScheme(),
                    maxValues
            );
            sb.append("- Overall Grade: ").append(String.format("%.2f", grade)).append("%\n");
            sb.append("- Individual Scores:\n");
            for (Map.Entry<String, String> entry : record.getGrades().entrySet()) {
                sb.append("  * ").append(entry.getKey()).append(": ")
                        .append(entry.getValue()).append("\n");
            }
            sb.append("\n");
        }

        sb.append("Please provide a comprehensive analysis of this class, including performance trends, ");
        sb.append("areas of strength and weakness, predictive analysis for future performance, ");
        sb.append("and actionable recommendations for the teacher.");

        return sb.toString();
    }
}
