package com.geo.controller;

import com.geo.common.Result;
import com.geo.dto.RpaCallbackRequest;
import com.geo.service.RpaDispatchService;
import com.geo.service.TaskService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/rpa")
public class RpaCallbackController {

    private static final Logger log = LoggerFactory.getLogger(RpaCallbackController.class);

    private final TaskService taskService;
    private final RpaDispatchService rpaDispatchService;

    public RpaCallbackController(TaskService taskService, RpaDispatchService rpaDispatchService) {
        this.taskService = taskService;
        this.rpaDispatchService = rpaDispatchService;
    }

    @PostMapping("/callback")
    public Result<String> handleRpaCallback(@RequestBody RpaCallbackRequest request) {
        log.info("收到 RPA 回调: taskResultId={}, status={}", request.getTaskResultId(), request.getStatus());
        taskService.updateTaskResult(
                request.getTaskResultId(),
                request.getAnswerText(),
                request.getScreenshotUrl(),
                request.getStatus(),
                request.getErrorMsg(),
                request.getDurationMs()
        );

        return Result.success("回调处理成功");
    }
}