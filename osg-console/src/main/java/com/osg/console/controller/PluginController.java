package com.osg.console.controller;

import com.osg.common.model.Result;
import com.osg.core.plugin.PluginEngine;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.web.bind.annotation.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 插件管理控制器
 */
@RestController
@RequestMapping("/console/plugin")
@Api(tags = "插件管理")
public class PluginController {

    private final PluginEngine pluginEngine;

    public PluginController(PluginEngine pluginEngine) {
        this.pluginEngine = pluginEngine;
    }

    @GetMapping("/list")
    @ApiOperation("获取插件列表")
    public Result<Map<String, Object>> list() {
        Map<String, Object> plugins = new HashMap<>();
        plugins.put("rateLimiters", pluginEngine.getRateLimiterTypes());
        plugins.put("authenticators", pluginEngine.getAuthenticatorTypes());
        plugins.put("protocols", pluginEngine.getProtocolTypes());
        return Result.success(plugins);
    }

    @PostMapping("/reload")
    @ApiOperation("热加载插件")
    public Result<Void> reload() {
        pluginEngine.hotReload();
        return Result.success();
    }
}
