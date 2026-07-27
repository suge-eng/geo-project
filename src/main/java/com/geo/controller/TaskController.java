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
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.concurrent.TimeUnit;

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
    public ResponseEntity<Result<TaskProgressVO>> getTaskProgress(@PathVariable String taskNo) {
        TaskProgressVO progress = taskService.getTaskProgress(taskNo);
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store, no-cache, must-revalidate, max-age=0")
                .header(HttpHeaders.PRAGMA, "no-cache")
                .header(HttpHeaders.EXPIRES, "0")
                .body(Result.success(progress));
    }

    @GetMapping("/{taskNo}/results")
    public ResponseEntity<Result<List<TaskResultVO>>> getTaskResults(@PathVariable String taskNo) {
        List<TaskResultVO> results = taskService.getTaskResults(taskNo);
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store, no-cache, must-revalidate, max-age=0")
                .header(HttpHeaders.PRAGMA, "no-cache")
                .header(HttpHeaders.EXPIRES, "0")
                .body(Result.success(results));
    }

    @GetMapping("/{taskNo}")
    public Result<Task> getTask(@PathVariable String taskNo) {
        Task task = taskService.getTaskByNo(taskNo);
        if (task == null) {
            return Result.fail(404, "任务不存在");
        }
        return Result.success(task);
    }

    @PostMapping("/{taskNo}/retry")
    public Result<Task> retryFailedTasks(@PathVariable String taskNo) {
        log.info("收到重试失败任务请求: taskNo={}", taskNo);
        Task task = taskService.retryFailedTasks(taskNo);
        return Result.success("失败任务已重新调度", task);
    }

    @PostMapping("/{taskNo}/retry/all")
    public Result<Task> retryAllTasks(@PathVariable String taskNo) {
        log.info("收到重试所有任务请求: taskNo={}", taskNo);
        Task task = taskService.retryAllTasks(taskNo);
        return Result.success("所有任务已重新调度", task);
    }

    @PostMapping("/{taskNo}/retry/success")
    public Result<Task> retrySuccessTasks(@PathVariable String taskNo) {
        log.info("收到重试成功任务请求: taskNo={}", taskNo);
        Task task = taskService.retrySuccessTasks(taskNo);
        return Result.success("成功任务已重新调度", task);
    }

    @PostMapping("/result/{taskResultId}/retry")
    public Result<Task> retrySpecificTaskResult(@PathVariable Long taskResultId) {
        log.info("收到重试单个任务结果请求: taskResultId={}", taskResultId);
        Task task = taskService.retrySpecificTaskResult(taskResultId);
        return Result.success("任务结果已重新调度", task);
    }
}