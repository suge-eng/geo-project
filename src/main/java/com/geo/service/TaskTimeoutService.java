package com.geo.service;

import com.geo.config.TaskTimeoutConfig;
import com.geo.entity.Task;
import com.geo.entity.TaskResult;
import com.geo.enums.ResultStatus;
import com.geo.enums.TaskStatus;
import com.geo.mapper.TaskMapper;
import com.geo.mapper.TaskResultMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class TaskTimeoutService {

    private static final Logger log = LoggerFactory.getLogger(TaskTimeoutService.class);

    private final TaskResultMapper taskResultMapper;
    private final TaskMapper taskMapper;
    private final TaskTimeoutConfig timeoutConfig;

    public TaskTimeoutService(TaskResultMapper taskResultMapper, TaskMapper taskMapper, 
                              TaskTimeoutConfig timeoutConfig) {
        this.taskResultMapper = taskResultMapper;
        this.taskMapper = taskMapper;
        this.timeoutConfig = timeoutConfig;
    }

    @Scheduled(fixedRateString = "${geo.task.timeout.check-interval-seconds:60}000")
    @Transactional
    public void checkTimeoutTasks() {
        log.debug("开始检查超时任务...");
        
        LocalDateTime timeoutTime = LocalDateTime.now().minusMinutes(timeoutConfig.getTimeoutMinutes());
        
        List<TaskResult> pendingResults = taskResultMapper.selectByStatus(ResultStatus.PENDING.name());
        
        int timeoutCount = 0;
        for (TaskResult result : pendingResults) {
            if (result.getCreatedAt() != null && result.getCreatedAt().isBefore(timeoutTime)) {
                markAsTimeout(result);
                timeoutCount++;
            }
        }
        
        if (timeoutCount > 0) {
            log.warn("检测到 {} 个超时任务", timeoutCount);
        }
    }

    private void markAsTimeout(TaskResult result) {
        result.setStatus(ResultStatus.TIMEOUT.name());
        result.setErrorMsg("任务执行超时，RPA未在规定时间内返回结果");
        result.setCompletedAt(LocalDateTime.now());
        taskResultMapper.updateById(result);
        
        log.warn("任务结果超时: taskNo={}, id={}, platform={}, question={}", 
                result.getTaskNo(), result.getId(), result.getAiPlatform(), result.getQuestionText());
        
        updateTaskProgress(result.getTaskId());
    }

    private void updateTaskProgress(Long taskId) {
        Task task = taskMapper.selectById(taskId);
        if (task == null) {
            return;
        }

        List<TaskResult> results = taskResultMapper.selectByTaskId(taskId);
        long successCount = 0;
        long failedCount = 0;
        long pendingCount = 0;
        for (TaskResult r : results) {
            String status = r.getStatus();
            if (ResultStatus.SUCCESS.name().equals(status)) {
                successCount++;
            } else if (ResultStatus.FAILED.name().equals(status) || ResultStatus.TIMEOUT.name().equals(status)) {
                failedCount++;
            } else {
                pendingCount++;
            }
        }

        task.setCompletedCount((int) successCount);
        task.setFailedCount((int) failedCount);

        if (pendingCount == 0) {
            if (failedCount == 0) {
                task.setStatus(TaskStatus.COMPLETED.name());
            } else if (successCount > 0) {
                task.setStatus(TaskStatus.PARTIAL_FAILED.name());
            } else {
                task.setStatus(TaskStatus.FAILED.name());
            }
            task.setCompletedAt(LocalDateTime.now());
        }

        taskMapper.updateById(task);
    }
}