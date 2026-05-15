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
                Trả về DUY NHẤT 1 JSON object hợp lệ, không markdown, không giải thích ngoài JSON.
                Chỉ dùng đúng các key: ds, algo, time, space, diff, ai, ai_reason, quality, summary.
                Format:
                {"ds":["Vector"],"algo":["DP"],"time":"O(n)","space":"O(n)","diff":"EASY","ai":5,"ai_reason":"Nhận xét ngắn về dấu hiệu AI","quality":80,"summary":"Nhận xét chi tiết bằng tiếng Việt"}
                Luật:
                - ds: 1-5 cấu trúc dữ liệu chính, tên tiếng Anh ngắn; nếu không có thì [].
                - algo: 1-5 thuật toán/kỹ thuật chính, tên tiếng Anh ngắn; nếu không có thì [].
                - time/space: độ phức tạp Big-O ước lượng, nếu không rõ ghi Unknown.
                - diff: BEGINNER/EASY/MEDIUM/HARD/EXPERT.
                - ai: số 0-100, mức nghi ngờ code có hỗ trợ AI; không khẳng định tuyệt đối.
                - ai_reason: tiếng Việt, 2-4 câu, nêu dấu hiệu cụ thể trong code như style, đặt tên biến,
                  cấu trúc lời giải, độ đều tay, comment, xử lý biên. Nếu không đủ căn cứ thì nói rõ là chỉ suy đoán.
                - quality: số 0-100, chất lượng code dựa trên tính đúng, rõ ràng, độ phức tạp, xử lý biên và khả năng bảo trì.
                - summary: tiếng Việt, 4-7 câu. Phải nêu rõ:
                  1) ý tưởng thuật toán chính,
                  2) vì sao chọn CTDL/thuật toán đó,
                  3) phân tích độ phức tạp,
                  4) điểm mạnh của code,
                  5) điểm yếu/rủi ro hoặc case biên cần chú ý,
                  6) gợi ý cải thiện cụ thể.
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
                dưới đây, hãy đánh giá tổng hợp năng lực của lập trình viên "%s" bằng JSON chi tiết, rõ ràng và có căn cứ.
                
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
                - ds_score: 0-100, điểm CTDL tổng hợp; cân nhắc độ đa dạng và cách dùng đúng.
                - ds_mastered: 3-8 CTDL đã thành thạo hoặc dùng ổn định; nếu thiếu dữ liệu thì [].
                - ds_learning: 3-8 CTDL biết nhưng chưa thành thạo hoặc cần luyện thêm; nếu thiếu dữ liệu thì [].
                - algo_score: 0-100, điểm thuật toán; cân nhắc độ đa dạng, độ khó và chất lượng triển khai.
                - algo_mastered: 3-8 thuật toán/kỹ thuật thành thạo; nếu thiếu dữ liệu thì [].
                - algo_learning: 3-8 thuật toán/kỹ thuật nên luyện thêm; nếu thiếu dữ liệu thì [].
                - overall_level: BEGINNER / INTERMEDIATE / ADVANCED / EXPERT
                - strengths: Tiếng Việt, 4-7 câu. Nêu rõ lập trình viên mạnh ở nhóm bài nào, CTDL/thuật toán nào,
                  dấu hiệu nào trong dữ liệu chứng minh điều đó, và mức ổn định khi giải bài.
                - weaknesses: Tiếng Việt, 4-7 câu. Nêu rõ lỗ hổng năng lực, nhóm kỹ thuật còn thiếu,
                  dấu hiệu từ dữ liệu, rủi ro khi gặp bài khó hơn, và phần nào cần kiểm chứng thêm.
                - recommendation: Tiếng Việt, 5-8 câu. Đưa lộ trình luyện tập cụ thể theo thứ tự ưu tiên,
                  gồm nhóm bài nên luyện, kỹ thuật cần bổ sung, cách giảm phụ thuộc AI nếu ai_usage cao,
                  và tiêu chí để biết đã tiến bộ.
                - Không viết chung chung kiểu "cần luyện thêm"; phải gắn với dữ liệu phân tích bên dưới.
                
                Dữ liệu phân tích:
                %s
                """.formatted(username, analysisData);
    }
}
