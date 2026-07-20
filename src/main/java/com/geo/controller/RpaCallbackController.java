// 完整的 RpaCallbackController.java（替换整个文件）
package com.geo.controller;

import com.geo.common.Result;
import com.geo.service.MinioService;
import com.geo.service.TaskService;
import com.geo.entity.TaskResult;
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
     * - questionText: 问题文本（可选，用于精确匹配某条记录）
     * - screenshotUrls: 截图URL列表
     * - status: 状态 (SUCCESS/FAILED)
     * - errorMsg: 错误信息
     * - durationMs: 耗时
     *
     * 匹配逻辑：
     * 1. 优先：task_no + question_text 精确匹配（如果传了questionText）
     * 2. 否则：找同 task_no 下还没有截图URL的那条记录更新
     */
    @PostMapping("/callback")
    @Transactional
    public Result<String> handleRpaCallback(@RequestBody Map<String, Object> request) {

        String taskNo = (String) request.get("taskNo");
        String questionText = (String) request.get("questionText");
        @SuppressWarnings("unchecked")
        List<String> screenshotUrls = (List<String>) request.get("screenshotUrls");
        String status = (String) request.get("status");
        String errorMsg = (String) request.get("errorMsg");
        Long durationMs = request.get("durationMs") != null ? ((Number) request.get("durationMs")).longValue() : null;

        log.info("收到RPA回调: taskNo={}, questionText={}, status={}", taskNo, questionText, status);

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

        // 方案A：如果传了 questionText，用 task_no + questionText 精确匹配
        if (questionText != null && !questionText.isEmpty()) {
            for (TaskResult r : results) {
                if (questionText.equals(r.getQuestionText())) {
                    targetResult = r;
                    log.info("用 questionText 精确匹配到记录: id={}", r.getId());
                    break;
                }
            }
        }

        // 方案B：没有传 questionText 或匹配失败，找第一条还没截图URL的记录
        if (targetResult == null) {
            for (TaskResult r : results) {
                String urls = r.getScreenshotUrls();
                if (urls == null || urls.isEmpty() || urls.equals("[]") || urls.equals("null")) {
                    targetResult = r;
                    log.info("找到未更新记录: id={}, questionText={}", r.getId(), r.getQuestionText());
                    break;
                }
            }
        }

        // 方案C：所有记录都有截图URL了，就更新第一条（兜底）
        if (targetResult == null) {
            targetResult = results.get(0);
            log.info("所有记录都已更新，更新第一条: id={}", targetResult.getId());
        }

        // 3. 更新记录
        targetResult.setStatus(status);
        targetResult.setErrorMsg(errorMsg);
        targetResult.setDurationMs(durationMs);
        targetResult.setCompletedAt(LocalDateTime.now());

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
}