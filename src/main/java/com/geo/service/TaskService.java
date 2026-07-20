package com.geo.service;

import com.geo.common.BusinessException;
import com.geo.common.ResultCode;
import com.geo.dto.TaskProgressVO;
import com.geo.dto.TaskResultVO;
import com.geo.entity.Task;
import com.geo.entity.TaskAi;
import com.geo.entity.TaskQuestion;
import com.geo.entity.TaskResult;
import com.geo.enums.AiPlatform;
import com.geo.enums.ResultStatus;
import com.geo.enums.TaskStatus;
import com.geo.mapper.TaskAiMapper;
import com.geo.mapper.TaskMapper;
import com.geo.mapper.TaskQuestionMapper;
import com.geo.mapper.TaskResultMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class TaskService {

    private static final Logger log = LoggerFactory.getLogger(TaskService.class);

    private final TaskMapper taskMapper;
    private final TaskAiMapper taskAiMapper;
    private final TaskQuestionMapper taskQuestionMapper;
    private final TaskResultMapper taskResultMapper;
    private final RpaDispatchService rpaDispatchService;
    private final ObjectMapper objectMapper;

    public TaskService(TaskMapper taskMapper, TaskAiMapper taskAiMapper,
                       TaskQuestionMapper taskQuestionMapper, TaskResultMapper taskResultMapper,
                       RpaDispatchService rpaDispatchService, ObjectMapper objectMapper) {
        this.taskMapper = taskMapper;
        this.taskAiMapper = taskAiMapper;
        this.taskQuestionMapper = taskQuestionMapper;
        this.taskResultMapper = taskResultMapper;
        this.rpaDispatchService = rpaDispatchService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public Task createTask(List<String> aiPlatforms, List<String> questions, String title,
                          String brandName, List<String> competitors, 
                          String executionFrequency, Boolean retryOnFailure) {
        validateAiPlatforms(aiPlatforms);
        validateQuestions(questions);
        validateBrandName(brandName);

        String taskNo = generateTaskNo();
        Task task = new Task();
        task.setTaskNo(taskNo);
        task.setUserId("anonymous");
        task.setTitle(title);
        task.setStatus(TaskStatus.PENDING.name());
        task.setTotalAiCount(aiPlatforms.size());
        task.setTotalQuestionCount(questions.size());
        task.setTotalCount(aiPlatforms.size() * questions.size());
        task.setCompletedCount(0);
        task.setFailedCount(0);
        
        taskMapper.insert(task);

        saveTaskAis(task.getId(), taskNo, aiPlatforms);
        List<TaskQuestion> savedQuestions = saveTaskQuestions(task.getId(), taskNo, questions);
        saveTaskCompetitors(task.getId(), taskNo, competitors);

        List<TaskResult> results = createTaskResults(task.getId(), taskNo, aiPlatforms, savedQuestions, brandName);
        for (TaskResult result : results) {
            taskResultMapper.insert(result);
        }

        task.setStatus(TaskStatus.PROCESSING.name());
        taskMapper.updateById(task);

        rpaDispatchService.dispatchTasks(taskNo, results, brandName, competitors, executionFrequency, retryOnFailure);

        return task;
    }

    private void validateBrandName(String brandName) {
        if (brandName == null || brandName.trim().isEmpty()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "自主品牌名不能为空");
        }
        if (brandName.length() > 100) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "自主品牌名长度不能超过100字符");
        }
    }

    private void validateAiPlatforms(List<String> aiPlatforms) {
        if (aiPlatforms == null || aiPlatforms.isEmpty()) {
            throw new BusinessException(ResultCode.AI_LIST_EMPTY);
        }
        for (String platform : aiPlatforms) {
            if (AiPlatform.fromCode(platform) == null) {
                throw new BusinessException(ResultCode.AI_PLATFORM_INVALID, "不支持的AI平台: " + platform);
            }
        }
    }

    private void validateQuestions(List<String> questions) {
        if (questions == null || questions.isEmpty()) {
            throw new BusinessException(ResultCode.QUESTION_EMPTY);
        }
        if (questions.size() > 50) {
            throw new BusinessException(ResultCode.QUESTION_TOO_MANY);
        }
        for (String question : questions) {
            if (question == null || question.trim().isEmpty()) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "问题不能为空");
            }
            if (question.length() > 1000) {
                throw new BusinessException(ResultCode.QUESTION_TOO_LONG);
            }
        }
    }

    private String generateTaskNo() {
        String timestamp = LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        String uuid = UUID.randomUUID().toString().substring(0, 8);
        return "T" + timestamp + uuid.toUpperCase();
    }

    private void saveTaskAis(Long taskId, String taskNo, List<String> aiPlatforms) {
        for (String platform : aiPlatforms) {
            AiPlatform aiPlatform = AiPlatform.fromCode(platform);
            TaskAi taskAi = new TaskAi();
            taskAi.setTaskId(taskId);
            taskAi.setTaskNo(taskNo);
            taskAi.setAiPlatform(platform);
            taskAi.setAiDisplayName(aiPlatform != null ? aiPlatform.getDisplayName() : platform);
            taskAiMapper.insert(taskAi);
        }
    }

    private List<TaskQuestion> saveTaskQuestions(Long taskId, String taskNo, List<String> questions) {
        List<TaskQuestion> taskQuestions = new ArrayList<>();
        int order = 0;
        for (String question : questions) {
            TaskQuestion tq = new TaskQuestion();
            tq.setTaskId(taskId);
            tq.setTaskNo(taskNo);
            tq.setQuestionText(question);
            tq.setSortOrder(order++);
            taskQuestionMapper.insert(tq);
            taskQuestions.add(tq);
        }
        return taskQuestions;
    }

    private void saveTaskCompetitors(Long taskId, String taskNo, List<String> competitors) {
        if (competitors == null || competitors.isEmpty()) {
            return;
        }
        for (String competitor : competitors) {
            if (competitor != null && !competitor.trim().isEmpty()) {
                TaskQuestion tq = new TaskQuestion();
                tq.setTaskId(taskId);
                tq.setTaskNo(taskNo);
                tq.setQuestionText("竞争对手: " + competitor.trim());
                tq.setSortOrder(999);
                taskQuestionMapper.insert(tq);
            }
        }
    }

    private List<TaskResult> createTaskResults(Long taskId, String taskNo,
                                                List<String> aiPlatforms,
                                                List<TaskQuestion> questions,
                                                String brandName) {
        List<TaskResult> results = new ArrayList<>();
        for (TaskQuestion question : questions) {
            for (String platform : aiPlatforms) {
                TaskResult result = new TaskResult();
                result.setTaskId(taskId);
                result.setTaskNo(taskNo);
                result.setTaskQuestionId(question.getId());
                result.setAiPlatform(platform);
                result.setQuestionText(question.getQuestionText());
                result.setStatus(ResultStatus.PENDING.name());
                result.setRpaRetryCount(0);
                results.add(result);
            }
        }
        return results;
    }

    public Task getTaskByNo(String taskNo) {
        com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<Task> wrapper = 
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<>();
        wrapper.eq("task_no", taskNo);
        return taskMapper.selectOne(wrapper);
    }

    public List<Task> listTasks() {
        com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<Task> wrapper = 
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<>();
        wrapper.orderByDesc("created_at");
        return taskMapper.selectList(wrapper);
    }

    public TaskProgressVO getTaskProgress(String taskNo) {
        Task task = getTaskByNo(taskNo);
        if (task == null) {
            throw new BusinessException(ResultCode.TASK_NOT_FOUND);
        }

        return TaskProgressVO.builder()
                .taskNo(task.getTaskNo())
                .status(task.getStatus())
                .totalCount(task.getTotalCount())
                .completedCount(task.getCompletedCount())
                .failedCount(task.getFailedCount())
                .totalAiCount(task.getTotalAiCount())
                .totalQuestionCount(task.getTotalQuestionCount())
                .percentage(task.getTotalCount() > 0 ?
                        (task.getCompletedCount().doubleValue() / task.getTotalCount()) * 100 : 0)
                .errorMsg(task.getErrorMsg())
                .createdAt(task.getCreatedAt())
                .completedAt(task.getCompletedAt())
                .build();
    }

    public List<TaskResultVO> getTaskResults(String taskNo) {
        Task task = getTaskByNo(taskNo);
        if (task == null) {
            throw new BusinessException(ResultCode.TASK_NOT_FOUND);
        }

        List<TaskAi> taskAis = taskAiMapper.selectByTaskNo(taskNo);
        Map<String, String> platformNameMap = new HashMap<>();
        for (TaskAi ai : taskAis) {
            platformNameMap.put(ai.getAiPlatform(), ai.getAiDisplayName());
        }

        List<TaskResult> results = taskResultMapper.selectByTaskNo(taskNo);
        List<TaskResultVO> voList = new ArrayList<>();
        for (TaskResult r : results) {
            TaskResultVO vo = TaskResultVO.builder()
                    .id(r.getId())
                    .aiPlatform(r.getAiPlatform())
                    .aiDisplayName(platformNameMap.getOrDefault(r.getAiPlatform(), r.getAiPlatform()))
                    .questionText(r.getQuestionText())
                    .answerText(r.getAnswerText())
                    .screenshotUrls(parseScreenshotUrls(r.getScreenshotUrls()))
                    .status(r.getStatus())
                    .errorMsg(r.getErrorMsg())
                    .durationMs(r.getDurationMs())
                    .rpaRetryCount(r.getRpaRetryCount())
                    .createdAt(r.getCreatedAt())
                    .completedAt(r.getCompletedAt())
                    .build();
            voList.add(vo);
        }
        return voList;
    }

    private java.util.List<String> parseScreenshotUrls(String screenshotUrlsJson) {
        if (screenshotUrlsJson == null || screenshotUrlsJson.isEmpty()) {
            return new java.util.ArrayList<>();
        }
        try {
            return objectMapper.readValue(screenshotUrlsJson, new TypeReference<List<String>>() {});
        } catch (JsonProcessingException e) {
            log.warn("解析截图URLs失败: {}", screenshotUrlsJson, e);
            return new java.util.ArrayList<>();
        }
    }

    @Transactional
    public void updateTaskResult(Long taskResultId, String answerText, String screenshotUrl,
                                 String status, String errorMsg, Long durationMs) {
        TaskResult result = taskResultMapper.selectById(taskResultId);
        if (result == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "任务结果不存在");
        }

        result.setAnswerText(answerText);
        if (screenshotUrl != null && !screenshotUrl.isEmpty()) {
            List<String> urls = new ArrayList<>();
            urls.add(screenshotUrl);
            try {
                result.setScreenshotUrls(objectMapper.writeValueAsString(urls));
            } catch (JsonProcessingException e) {
                log.error("序列化截图URL失败", e);
            }
        }
        result.setStatus(status);
        result.setErrorMsg(errorMsg);
        result.setDurationMs(durationMs);
        result.setCompletedAt(LocalDateTime.now());
        taskResultMapper.updateById(result);

        updateTaskProgress(result.getTaskId());
    }

    @Transactional
    public void updateTaskResultWithMultipleScreenshots(Long taskResultId, String answerText, 
                                                        java.util.List<String> screenshotUrls,
                                                        String status, String errorMsg, Long durationMs) {
        TaskResult result = taskResultMapper.selectById(taskResultId);
        if (result == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "任务结果不存在");
        }

        result.setAnswerText(answerText);
        if (screenshotUrls != null && !screenshotUrls.isEmpty()) {
            try {
                result.setScreenshotUrls(objectMapper.writeValueAsString(screenshotUrls));
            } catch (JsonProcessingException e) {
                log.error("序列化截图URLs失败", e);
            }
        }
        result.setStatus(status);
        result.setErrorMsg(errorMsg);
        result.setDurationMs(durationMs);
        result.setCompletedAt(LocalDateTime.now());
        taskResultMapper.updateById(result);

        updateTaskProgress(result.getTaskId());
    }

    @Transactional
    public void updateTaskProgress(Long taskId) {
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