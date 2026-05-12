package com.codeanalyzer.ai;

import com.codeanalyzer.config.AppConfig;

/**
 * Xây dựng prompt phân tích code cho Gemini API.
 */
public class CodeAnalysisPrompt {

    /**
     * Tạo prompt phân tích cho một submission.
     */
    public static String buildAnalysisPrompt(String sourceCode, String language, String problemName) {
        // Cắt code nếu quá dài
        String code = sourceCode == null ? "" : sourceCode;
        int maxCodeLength = AppConfig.getAnalysisMaxCodeLength();
        if (code.length() > maxCodeLength) {
            code = code.substring(0, maxCodeLength) + "\n// ... (code đã bị cắt bớt)";
        }

        return """
                Trả về DUY NHẤT 1 JSON object ngắn, không markdown, không giải thích.
                Chỉ dùng đúng các key: ds, algo, time, space, diff, ai, ai_reason, quality, summary.
                Format:
                {"ds":["Vector"],"algo":["DP"],"time":"O(n)","space":"O(n)","diff":"EASY","ai":5,"ai_reason":"Style tự nhiên, ít dấu hiệu AI","quality":80,"summary":"Tóm tắt <=80 ký tự"}
                Luật:
                - ds: tối đa 3 cấu trúc dữ liệu chính, tên tiếng Anh ngắn.
                - algo: tối đa 3 thuật toán chính, tên tiếng Anh ngắn.
                - time/space: độ phức tạp Big-O ước lượng, nếu không rõ ghi Unknown.
                - diff: BEGINNER/EASY/MEDIUM/HARD/EXPERT.
                - ai: số 0-100, mức nghi ngờ code có hỗ trợ AI.
                - ai_reason: tiếng Việt, tối đa 100 ký tự, nêu dấu hiệu chính; không khẳng định tuyệt đối.
                - quality: số 0-100, chất lượng code.
                - summary: tiếng Việt, tối đa 80 ký tự.
                - Không thêm trường khác.

                Language: %s
                Problem: %s
                Code:
                %s
                """.formatted(language, problemName, code);
    }

    /**
     * Tạo prompt đánh giá tổng hợp cho một account.
     */
    public static String buildEvaluationPrompt(String username, String analysisData) {
        return """
                Bạn là chuyên gia đánh giá năng lực lập trình viên. Dựa vào tổng hợp kết quả phân tích các bài submission \
                dưới đây, hãy đánh giá tổng hợp năng lực của lập trình viên "%s" bằng JSON ngắn gọn.
                
                Trả về JSON theo schema sau. Không giải thích gì thêm ngoài JSON.
                
                {
                  "ds_score": number,
                  "ds_mastered": [string],
                  "ds_learning": [string],
                  "algo_score": number,
                  "algo_mastered": [string],
                  "algo_learning": [string],
                  "overall_level": string,
                  "strengths": string,
                  "weaknesses": string,
                  "recommendation": string
                }
                
                Quy tắc:
                - ds_score: 0-100, điểm CTDL tổng hợp
                - ds_mastered: Danh sách CTDL đã thành thạo (dùng nhiều lần, đúng cách)
                - ds_learning: Danh sách CTDL biết nhưng chưa thành thạo
                - algo_score: 0-100, điểm thuật toán
                - algo_mastered: Thuật toán thành thạo
                - algo_learning: Thuật toán đang học
                - overall_level: BEGINNER / INTERMEDIATE / ADVANCED / EXPERT
                - strengths: Tiếng Việt, tối đa 180 ký tự
                - weaknesses: Tiếng Việt, tối đa 180 ký tự
                - recommendation: Tiếng Việt, tối đa 240 ký tự
                
                Dữ liệu phân tích:
                %s
                """.formatted(username, analysisData);
    }
}
