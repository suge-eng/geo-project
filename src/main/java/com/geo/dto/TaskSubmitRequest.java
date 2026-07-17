package com.geo.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public class TaskSubmitRequest {

    @NotEmpty(message = "至少选择一个AI平台")
    @Size(max = 10, message = "AI平台数量不能超过10个")
    private List<String> aiPlatforms;

    @NotEmpty(message = "问题列表不能为空")
    @Size(max = 50, message = "问题数量不能超过50个")
    private List<String> questions;

    @Size(max = 200, message = "标题长度不能超过200字符")
    private String title;

    public List<String> getAiPlatforms() { return aiPlatforms; }
    public void setAiPlatforms(List<String> aiPlatforms) { this.aiPlatforms = aiPlatforms; }
    public List<String> getQuestions() { return questions; }
    public void setQuestions(List<String> questions) { this.questions = questions; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
}