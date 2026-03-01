package com.osg.console.controller;

import com.osg.common.model.ApiMetadata;
import com.osg.common.model.Result;
import com.osg.core.config.ConfigEngine;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * API管理控制器
 */
@RestController
@RequestMapping("/console/api")
@Api(tags = "API管理")
public class ApiController {

    private final ConfigEngine configEngine;

    public ApiController(ConfigEngine configEngine) {
        this.configEngine = configEngine;
    }

    @GetMapping("/list")
    @ApiOperation("获取API列表")
    public Result<List<ApiMetadata>> list() {
        List<ApiMetadata> apis = configEngine.getAllApiMetadata().values().stream()
            .collect(Collectors.toList());
        return Result.success(apis);
    }

    @GetMapping("/{apiId}")
    @ApiOperation("获取API详情")
    public Result<ApiMetadata> get(@PathVariable String apiId) {
        ApiMetadata metadata = configEngine.getApiMetadata(apiId);
        if (metadata == null) {
            return Result.error("API_NOT_FOUND", "API不存在");
        }
        return Result.success(metadata);
    }

    @PostMapping("/register")
    @ApiOperation("注册API")
    public Result<Void> register(@RequestBody ApiMetadata apiMetadata) {
        configEngine.registerApiMetadata(apiMetadata);
        return Result.success();
    }

    @DeleteMapping("/{apiId}")
    @ApiOperation("删除API")
    public Result<Void> delete(@PathVariable String apiId) {
        configEngine.removeApiMetadata(apiId);
        return Result.success();
    }

    @PutMapping("/{apiId}")
    @ApiOperation("更新API")
    public Result<Void> update(@PathVariable String apiId, @RequestBody ApiMetadata apiMetadata) {
        apiMetadata.setApiId(apiId);
        configEngine.registerApiMetadata(apiMetadata);
        return Result.success();
    }
}
