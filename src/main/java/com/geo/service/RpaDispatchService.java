package com.geo.service;

import com.geo.config.RabbitMQConfig;
import com.geo.entity.TaskResult;
import com.geo.enums.AiPlatform;
import com.geo.websocket.ProgressWebSocketHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class RpaDispatchService {

    private static final Logger log = LoggerFactory.getLogger(RpaDispatchService.class);

    private final RabbitTemplate rabbitTemplate;
    private final ProgressWebSocketHandler webSocketHandler;

    public RpaDispatchService(RabbitTemplate rabbitTemplate, ProgressWebSocketHandler webSocketHandler) {
        this.rabbitTemplate = rabbitTemplate;
        this.webSocketHandler = webSocketHandler;
    }

    @Async("rpaDispatchExecutor")
    public void dispatchTasks(String taskNo, List<TaskResult> results) {
        log.info("开始调度任务: taskNo={}, count={}", taskNo, results.size());

        RpaBatchTaskMessage batchMessage = new RpaBatchTaskMessage();
        batchMessage.setTaskId(taskNo);
        batchMessage.setNeedScreenshot(true);
        batchMessage.setOutputDir("D:/GEO_RPA/result/" + taskNo);

        Map<String, String> platformCodeToName = new HashMap<>();
        for (AiPlatform platform : AiPlatform.values()) {
            platformCodeToName.put(platform.getCode(), platform.getDisplayName());
        }

        List<String> agentList = new ArrayList<>();
        List<RpaBatchTaskMessage.Question> questionList = new ArrayList<>();
        Map<String, String> platformUsed = new HashMap<>();
        Map<String, String> questionUsed = new HashMap<>();
        int qIndex = 1;

        for (TaskResult result : results) {
            String platformName = platformCodeToName.getOrDefault(result.getAiPlatform(), result.getAiPlatform());
            if (!platformUsed.containsKey(result.getAiPlatform())) {
                agentList.add(platformName);
                platformUsed.put(result.getAiPlatform(), platformName);
            }

            String questionKey = result.getQuestionText();
            if (!questionUsed.containsKey(questionKey)) {
                RpaBatchTaskMessage.Question question = new RpaBatchTaskMessage.Question();
               // question.setQId("Q" + String.format("%03d", qIndex++));
                question.setContent(result.getQuestionText());
                questionList.add(question);
                questionUsed.put(questionKey, question.getQId());
            }
        }

        batchMessage.setAgentList(agentList);
        batchMessage.setQuestionList(questionList);

        try {
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.RPA_TASK_EXCHANGE,
                    RabbitMQConfig.RPA_TASK_ROUTING_KEY,
                    batchMessage
            );
            log.info("RPA批量任务已发送: taskNo={}, agents={}, questions={}", 
                    taskNo, agentList.size(), questionList.size());
        } catch (Exception e) {
            log.error("发送 RPA 批量任务失败: taskNo={}", taskNo, e);
        }
    }

    public void sendProgress(String taskNo, String currentAi, String currentQuestion, double percentage) {
        Map<String, Object> progress = new HashMap<>();
        progress.put("type", "PROGRESS");
        progress.put("taskNo", taskNo);
        progress.put("currentAi", currentAi);
        progress.put("currentQuestion", currentQuestion);
        progress.put("percentage", percentage);
        webSocketHandler.sendProgress(taskNo, progress);
    }

    public void sendComplete(String taskNo) {
        webSocketHandler.sendComplete(taskNo);
    }

    public static class RpaBatchTaskMessage {
        private String taskId;
        private List<String> agentList;
        private List<Question> questionList;
        private boolean needScreenshot;
        private String outputDir;

        public String getTaskId() { return taskId; }
        public void setTaskId(String taskId) { this.taskId = taskId; }
        public List<String> getAgentList() { return agentList; }
        public void setAgentList(List<String> agentList) { this.agentList = agentList; }
        public List<Question> getQuestionList() { return questionList; }
        public void setQuestionList(List<Question> questionList) { this.questionList = questionList; }
        public boolean isNeedScreenshot() { return needScreenshot; }
        public void setNeedScreenshot(boolean needScreenshot) { this.needScreenshot = needScreenshot; }
        public String getOutputDir() { return outputDir; }
        public void setOutputDir(String outputDir) { this.outputDir = outputDir; }

        public static class Question {
            private String qId;
            private String content;

            public String getQId() { return qId; }
            public void setQId(String qId) { this.qId = qId; }
            public String getContent() { return content; }
            public void setContent(String content) { this.content = content; }
        }
    }
}