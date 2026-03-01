package com.osg.console.controller;

import com.osg.common.model.Result;
import com.osg.core.monitor.MonitorEngine;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.web.bind.annotation.*;
import java.util.HashMap;
import java.util.Map;

/**
 * 监控控制器
 */
@RestController
@RequestMapping("/console/monitor")
@Api(tags = "监控管理")
public class MonitorController {

    private final MonitorEngine monitorEngine;

    public MonitorController(MonitorEngine monitorEngine) {
        this.monitorEngine = monitorEngine;
    }

    @GetMapping("/stats")
    @ApiOperation("获取监控统计")
    public Result<Map<String, Object>> stats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("requestCount", monitorEngine.getRequestCount());
        stats.put("errorCount", monitorEngine.getErrorCount());
        stats.put("errorRate", monitorEngine.getErrorRate());
        return Result.success(stats);
    }
}
