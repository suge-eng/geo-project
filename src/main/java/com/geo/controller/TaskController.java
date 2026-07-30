package com.geo.controller;

import com.geo.common.Result;
import com.geo.dto.ExcelTaskSubmitRequest;
import com.geo.dto.TaskProgressVO;
import com.geo.dto.TaskResultVO;
import com.geo.dto.TaskSubmitRequest;
import com.geo.entity.Task;
import com.geo.service.ExcelParseService;
import com.geo.service.TaskService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/task")
public class TaskController {

    private static final Logger log = LoggerFactory.getLogger(TaskController.class);

    private final TaskService taskService;
    private final ExcelParseService excelParseService;

    public TaskController(TaskService taskService, ExcelParseService excelParseService) {
        this.taskService = taskService;
        this.excelParseService = excelParseService;
    }

    @GetMapping("/list")
    public Result<List<Task>> listTasks() {
        List<Task> tasks = taskService.listTasks();
        return Result.success(tasks);
    }

    @PostMapping("/create")
    public Result<Task> createTask(@Valid @RequestBody TaskSubmitRequest request) {
        log.info("收到任务创建请求: aiPlatforms={}, questionCount={}, brandName={}",
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
        return Result.success("任务创建成功", task);
    }

    @PostMapping("/{taskNo}/submit")
    public Result<Task> submitTask(@PathVariable String taskNo, 
                                   @RequestBody Map<String, Object> request) {
        String executionFrequency = (String) request.getOrDefault("executionFrequency", "single");
        Boolean retryOnFailure = (Boolean) request.getOrDefault("retryOnFailure", false);
        log.info("收到任务提交请求: taskNo={}, executionFrequency={}, retryOnFailure={}", 
                taskNo, executionFrequency, retryOnFailure);
        Task task = taskService.submitTask(taskNo, executionFrequency, retryOnFailure);
        return Result.success("任务已提交", task);
    }

    @DeleteMapping("/{taskNo}")
    public Result<Void> deleteTask(@PathVariable String taskNo) {
        log.info("收到任务删除请求: taskNo={}", taskNo);
        taskService.deleteTask(taskNo);
        return Result.success("任务已删除");
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

    @PostMapping(value = "/create/excel", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<Task> createTaskFromExcel(
            @RequestParam("file") MultipartFile file,
            @RequestParam("aiPlatforms") List<String> aiPlatforms,
            @RequestParam("title") String title,
            @RequestParam("brandName") String brandName,
            @RequestParam(value = "competitors", required = false) List<String> competitors,
            @RequestParam(value = "executionFrequency", defaultValue = "single") String executionFrequency,
            @RequestParam(value = "retryOnFailure", defaultValue = "false") Boolean retryOnFailure) {
        
        log.info("收到Excel任务创建请求: filename={}, aiPlatforms={}, title={}, brandName={}",
                file.getOriginalFilename(), aiPlatforms, title, brandName);
        
        List<String> questions = excelParseService.parseQuestionsFromExcel(file);
        log.info("从Excel解析出 {} 个问题", questions.size());
        
        Task task = taskService.createTask(
                aiPlatforms,
                questions,
                title,
                brandName,
                competitors,
                executionFrequency,
                retryOnFailure
        );
        return Result.success("Excel任务创建成功", task);
    }
}