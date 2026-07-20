package com.geo.controller;

import com.geo.common.Result;
import com.geo.dto.TaskProgressVO;
import com.geo.dto.TaskResultVO;
import com.geo.dto.TaskSubmitRequest;
import com.geo.entity.Task;
import com.geo.service.TaskService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/task")
public class TaskController {

    private static final Logger log = LoggerFactory.getLogger(TaskController.class);

    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    @GetMapping("/list")
    public Result<List<Task>> listTasks() {
        List<Task> tasks = taskService.listTasks();
        return Result.success(tasks);
    }

    @PostMapping("/submit")
    public Result<Task> submitTask(@Valid @RequestBody TaskSubmitRequest request) {
        log.info("收到任务提交请求: aiPlatforms={}, questionCount={}, brandName={}",
                request.getAiPlatforms(), request.getQuestions().size(), request.getBrandName());
        
        Task task = taskService.createTask(
                request.getAiPlatforms(),
                request.getQuestions(),
                request.getTitle(),
                request.getBrandName(),
                request.getCompetitors(),
                request.getExecutionFrequency(),
                request.getRetryOnFailure()
        );
        return Result.success("任务已提交", task);
    }

    @GetMapping("/{taskNo}/progress")
    public Result<TaskProgressVO> getTaskProgress(@PathVariable String taskNo) {
        TaskProgressVO progress = taskService.getTaskProgress(taskNo);
        return Result.success(progress);
    }

    @GetMapping("/{taskNo}/results")
    public Result<List<TaskResultVO>> getTaskResults(@PathVariable String taskNo) {
        List<TaskResultVO> results = taskService.getTaskResults(taskNo);
        return Result.success(results);
    }

    @GetMapping("/{taskNo}")
    public Result<Task> getTask(@PathVariable String taskNo) {
        Task task = taskService.getTaskByNo(taskNo);
        if (task == null) {
            return Result.fail(404, "任务不存在");
        }
        return Result.success(task);
    }
}