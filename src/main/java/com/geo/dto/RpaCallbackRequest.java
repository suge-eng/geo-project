package com.geo.dto;

public class RpaCallbackRequest {

    private Long taskResultId;
    private String answerText;
    private String screenshotUrl;
    private java.util.List<String> screenshotUrls;
    private String status;
    private String errorMsg;
    private Long durationMs;

    public Long getTaskResultId() { return taskResultId; }
    public void setTaskResultId(Long taskResultId) { this.taskResultId = taskResultId; }
    public String getAnswerText() { return answerText; }
    public void setAnswerText(String answerText) { this.answerText = answerText; }
    public String getScreenshotUrl() { return screenshotUrl; }
    public void setScreenshotUrl(String screenshotUrl) { this.screenshotUrl = screenshotUrl; }
    public java.util.List<String> getScreenshotUrls() { return screenshotUrls; }
    public void setScreenshotUrls(java.util.List<String> screenshotUrls) { this.screenshotUrls = screenshotUrls; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getErrorMsg() { return errorMsg; }
    public void setErrorMsg(String errorMsg) { this.errorMsg = errorMsg; }
    public Long getDurationMs() { return durationMs; }
    public void setDurationMs(Long durationMs) { this.durationMs = durationMs; }
}