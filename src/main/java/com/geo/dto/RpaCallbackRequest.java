package com.geo.dto;

public class RpaCallbackRequest {

    private Long taskResultId;
    private String answerText;
    private String screenshotUrl;
    private String status;
    private String errorMsg;
    private Long durationMs;

    public Long getTaskResultId() { return taskResultId; }
    public void setTaskResultId(Long taskResultId) { this.taskResultId = taskResultId; }
    public String getAnswerText() { return answerText; }
    public void setAnswerText(String answerText) { this.answerText = answerText; }
    public String getScreenshotUrl() { return screenshotUrl; }
    public void setScreenshotUrl(String screenshotUrl) { this.screenshotUrl = screenshotUrl; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getErrorMsg() { return errorMsg; }
    public void setErrorMsg(String errorMsg) { this.errorMsg = errorMsg; }
    public Long getDurationMs() { return durationMs; }
    public void setDurationMs(Long durationMs) { this.durationMs = durationMs; }
}