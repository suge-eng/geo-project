// 完整的 RpaCallbackController.java（替换整个文件）
package com.geo.controller;

import com.geo.common.Result;
import com.geo.service.MinioService;
import com.geo.service.TaskService;
import com.geo.entity.TaskResult;
import com.geo.enums.AiPlatform;
import com.geo.enums.ResultStatus;
import com.geo.mapper.TaskResultMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/rpa")
public class RpaCallbackController {

    private static final Logger log = LoggerFactory.getLogger(RpaCallbackController.class);

    private final TaskService taskService;
    private final MinioService minioService;
    private final TaskResultMapper taskResultMapper;
    private final ObjectMapper objectMapper;

    public RpaCallbackController(TaskService taskService, MinioService minioService,
                                  TaskResultMapper taskResultMapper, ObjectMapper objectMapper) {
        this.taskService = taskService;
        this.minioService = minioService;
        this.taskResultMapper = taskResultMapper;
        this.objectMapper = objectMapper;
    }

    /**
     * 影刀RPA截图上传接口
     * POST /api/rpa/upload
     * Content-Type: multipart/form-data
     */
    @PostMapping("/upload")
    public Result<String> uploadScreenshot(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "taskId", required = false) String taskId) {

        log.info("收到RPA截图上传: taskId={}, filename={}, size={}KB",
                taskId, file.getOriginalFilename(), file.getSize() / 1024);

        if (file == null || file.isEmpty()) {
            return Result.fail(400, "上传文件不能为空");
        }

        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            return Result.fail(400, "只能上传图片文件");
        }

        try {
            String fileUrl = minioService.uploadFile(file);
            log.info("截图上传成功: taskId={}, url={}", taskId, fileUrl);
            return Result.success("上传成功", fileUrl);
        } catch (Exception e) {
            log.error("截图上传失败: taskId={}", taskId, e);
            return Result.fail(500, "上传失败: " + e.getMessage());
        }
    }

    /**
     * 影刀RPA任务完成回调接口
     * POST /api/rpa/callback
     * Content-Type: application/json
     *
     * 请求参数：
     * - taskNo: 任务ID (如 T20260719...)
     * - taskResultId: 任务结果ID（可选，用于精确匹配某条记录）
     * - aiPlatform: AI平台（可选，用于精确匹配）
     * - questionText: 问题文本（可选，用于精确匹配某条记录）
     * - screenshotUrls: 截图URL列表
     * - status: 状态 (SUCCESS/FAILED)
     * - errorMsg: 错误信息
     * - durationMs: 耗时
     *
     * 匹配逻辑：
     * 1. 优先：直接用 taskResultId 精确匹配
     * 2. 其次：task_no + aiPlatform + questionText 精确匹配
     * 3. 再次：task_no + questionText + PENDING状态 匹配
     * 4. 然后：task_no + questionText 匹配
     * 5. 再然后：找同 task_no 下还没有截图URL的那条记录
     * 6. 最后：更新第一条记录（兜底）
     */
    @PostMapping("/callback")
    @Transactional
    public Result<String> handleRpaCallback(@RequestBody Map<String, Object> request) {

        String taskNo = (String) request.get("taskNo");
        Long taskResultId = request.get("taskResultId") != null ? ((Number) request.get("taskResultId")).longValue() : null;
        String aiPlatform = (String) request.get("aiPlatform");
        String questionText = (String) request.get("questionText");
        @SuppressWarnings("unchecked")
        List<String> screenshotUrls = (List<String>) request.get("screenshotUrls");
        String status = (String) request.get("status");
        String errorMsg = (String) request.get("errorMsg");
        Long durationMs = request.get("durationMs") != null ? ((Number) request.get("durationMs")).longValue() : null;

        log.info("收到RPA回调: taskNo={}, taskResultId={}, aiPlatform={}, questionText={}, status={}", taskNo, taskResultId, aiPlatform, questionText, status);

        if (taskNo == null || taskNo.isEmpty()) {
            log.warn("回调中taskNo为空，跳过更新");
            return Result.fail(400, "taskNo不能为空");
        }

        // 1. 用 task_no 查询所有匹配记录
        QueryWrapper<TaskResult> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("task_no", taskNo);
        List<TaskResult> results = taskResultMapper.selectList(queryWrapper);

        if (results == null || results.isEmpty()) {
            log.warn("未找到匹配的任务结果: taskNo={}", taskNo);
            return Result.fail(404, "未找到匹配的任务记录");
        }

        log.info("查询到 {} 条 taskNo={} 的记录", results.size(), taskNo);

        // 2. 确定要更新哪条记录
        TaskResult targetResult = null;

        // 如果没有传 aiPlatform，尝试从截图URL中提取平台名称
        if (aiPlatform == null || aiPlatform.isEmpty()) {
            String extractedName = extractPlatformFromScreenshotUrl(screenshotUrls);
            if (extractedName != null) {
                log.info("从截图URL中提取到平台名称: {}", extractedName);
                // 尝试将显示名称转换为平台代码
                String code = getPlatformCodeFromDisplayName(extractedName);
                if (!extractedName.equals(code)) {
                    log.info("平台显示名称 {} 转换为代码: {}", extractedName, code);
                }
                aiPlatform = code;
            }
        }

        // 方案A：优先用 taskResultId 精确匹配
        if (taskResultId != null) {
            for (TaskResult r : results) {
                if (taskResultId.equals(r.getId())) {
                    targetResult = r;
                    log.info("用 taskResultId 精确匹配到记录: id={}", r.getId());
                    break;
                }
            }
        }

        // 方案B：必须同时有 aiPlatform 和 questionText 才能精确匹配
        if (targetResult == null && aiPlatform != null && !aiPlatform.isEmpty() && questionText != null && !questionText.isEmpty()) {
            for (TaskResult r : results) {
                // 尝试用平台代码匹配
                if (aiPlatform.equals(r.getAiPlatform()) && questionText.equals(r.getQuestionText())) {
                    targetResult = r;
                    log.info("用 aiPlatform + questionText 精确匹配到记录: id={}, platform={}, question={}", r.getId(), aiPlatform, questionText);
                    break;
                }
            }
        }

        // 方案C：如果平台代码匹配失败，尝试用显示名称匹配
        if (targetResult == null && aiPlatform != null && !aiPlatform.isEmpty() && questionText != null && !questionText.isEmpty()) {
            for (TaskResult r : results) {
                // 尝试用显示名称匹配
                if (questionText.equals(r.getQuestionText())) {
                    // 比较平台显示名称（从枚举获取）
                    String displayName = getPlatformDisplayName(r.getAiPlatform());
                    if (aiPlatform.equals(displayName)) {
                        targetResult = r;
                        log.info("用平台显示名称 + questionText 匹配到记录: id={}, platform={}, question={}", r.getId(), aiPlatform, questionText);
                        break;
                    }
                }
            }
        }

        // 如果没有精确匹配到，记录警告并返回错误
        if (targetResult == null) {
            log.warn("无法精确匹配到任务记录: taskNo={}, aiPlatform={}, questionText={}, taskResultId={}", taskNo, aiPlatform, questionText, taskResultId);
            log.warn("可用记录列表:");
            for (TaskResult r : results) {
                log.warn("  - id={}, aiPlatform={}, questionText={}, status={}", r.getId(), r.getAiPlatform(), r.getQuestionText(), r.getStatus());
            }
            return Result.fail(400, "无法精确匹配到任务记录，请确保RPA回调时传回 aiPlatform 参数");
        }

        // 3. 更新记录
        targetResult.setStatus(status);
        targetResult.setErrorMsg(errorMsg);
        targetResult.setDurationMs(durationMs);
        targetResult.setCompletedAt(LocalDateTime.now());

        String answerText = (String) request.get("answerText");
        String thinkingContent = (String) request.get("thinkingContent");
        String sourceInfo = (String) request.get("sourceInfo");
        
        if (answerText != null) {
            targetResult.setAnswerText(answerText);
        }
        if (thinkingContent != null) {
            targetResult.setThinkingContent(thinkingContent);
        }
        if (sourceInfo != null) {
            targetResult.setSourceInfo(sourceInfo);
        }

        if (screenshotUrls != null && !screenshotUrls.isEmpty()) {
            try {
                targetResult.setScreenshotUrls(objectMapper.writeValueAsString(screenshotUrls));
            } catch (Exception e) {
                log.error("序列化截图URL失败", e);
            }
        }

        taskResultMapper.updateById(targetResult);

        // 4. 更新任务进度
        taskService.updateTaskProgress(targetResult.getTaskId());

        log.info("任务结果更新成功: id={}, questionText={}, status={}, screenshotUrls={}",
                targetResult.getId(), targetResult.getQuestionText(), status, screenshotUrls);

        return Result.success("回调处理成功");
    }

    /**
     * 从截图URL中提取平台名称
     * 截图文件名格式: T20260720145658066C06A4_文心一言_1784540535.png
     */
    private String extractPlatformFromScreenshotUrl(List<String> screenshotUrls) {
        if (screenshotUrls == null || screenshotUrls.isEmpty()) {
            return null;
        }
        String url = screenshotUrls.get(0);
        // 从URL中提取文件名
        String filename = url.substring(url.lastIndexOf('/') + 1);
        // 文件名格式: Txxx_平台名_timestamp.png
        String[] parts = filename.split("_");
        if (parts.length >= 2) {
            return parts[1];
        }
        return null;
    }

    /**
     * 获取平台的显示名称
     */
    private String getPlatformDisplayName(String platformCode) {
        for (AiPlatform platform : AiPlatform.values()) {
            if (platform.getCode().equals(platformCode)) {
                return platform.getDisplayName();
            }
        }
        return platformCode;
    }

    /**
     * 从显示名称获取平台代码（反向查找）
     */
    private String getPlatformCodeFromDisplayName(String displayName) {
        if (displayName == null) {
            return null;
        }
        for (AiPlatform platform : AiPlatform.values()) {
            if (platform.getDisplayName().equals(displayName)) {
                return platform.getCode();
            }
        }
        return displayName;
    }
}