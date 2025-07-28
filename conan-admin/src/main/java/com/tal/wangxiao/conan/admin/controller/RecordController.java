package com.tal.wangxiao.conan.admin.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tal.wangxiao.conan.admin.service.RecordService;
import com.tal.wangxiao.conan.admin.service.ReplayService;
import com.tal.wangxiao.conan.common.api.ApiResponse;
import com.tal.wangxiao.conan.common.api.ResponseCode;
import com.tal.wangxiao.conan.common.controller.ConanBaseController;
import com.tal.wangxiao.conan.common.entity.db.RecordResult;
import com.tal.wangxiao.conan.common.model.Result;
import com.tal.wangxiao.conan.common.model.bo.RecordInfo;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;

/**
 * 任务回放Controller
 *
 * @author huyaoguo
 * @date 2021/1/4
 */
@RestController
@RequestMapping("/api/1.0/admin/record")
@Api(value = "流量录制", tags = "流量录制模块管理")
@Slf4j
public class RecordController extends ConanBaseController {
    @Resource
    private RecordService recordService;

    @ApiOperation(value = "查询录制日志", notes = "根据执行ID查询录制日志")
    @ApiImplicitParam(name = "task_execution_id", value = "任务执行Id", dataType = "int", paramType = "query")
    @GetMapping(value = "/log")
    //@PreAuthorize("@ss.hasPermi('admin:execution:record:log')")
    public ApiResponse<Object> findRecordLog(@RequestParam(value = "task_execution_id") Integer taskExecutionId){
        log.info("RecordController#findRecordLog:"+taskExecutionId);
        Result<Object> result = recordService.findLogByExecutionId(taskExecutionId);
        return  new ApiResponse<>(result);
    }

    @ApiOperation(value = "查询录制进度", notes = "根据执行ID查询录制进度")
    @ApiImplicitParam(name = "task_execution_id", value = "任务执行Id", dataType = "int", paramType = "query")
    @GetMapping(value = "/progress")
    public ApiResponse<String> findRecordProgress(@RequestParam(value = "task_execution_id") Integer taskExecutionId){
        log.info("RecordController#findReplayProgress:"+taskExecutionId);
        Result<String> result = recordService.findRecordProgress(taskExecutionId);
        return  new ApiResponse<>(result);
    }

    @Cacheable(value = "record-info")
    @GetMapping(value = "")
    @ApiImplicitParam(name = "task_execution_id", value = "任务执行Id", dataType = "int", paramType = "query")
    public ApiResponse<RecordInfo> findApi(@RequestParam(value = "task_execution_id") Integer taskExecutionId) throws Exception {
        log.info("RecordController#findApi 查询录制详情"+taskExecutionId);
        Result<Object> result = recordService.findByTaskExecutionId(taskExecutionId);
        return  new ApiResponse(result);
    }

    @ApiOperation(value = "通过域名下载流量", notes = "通过域名下载流量",produces = "application/json")
    @GetMapping(value = "/downloadByDomain")
    public void downloadByDomain(@RequestParam(value = "domain",required = false) String domainName,
                             HttpServletResponse response) throws Exception, IOException {
        log.info("RecordController#downloadByDomain:"+domainName);
        recordService.getGetFlowsByDomain(domainName,response);
    }

    @ApiOperation(value = "通过Es查询获取日志中的接口流量", notes = "通过Es查询获取日志中的接口流量",produces = "application/json")
    @GetMapping(value = "/downloadByEsQuery")
    public void downloadByEsQuery(@RequestParam(value = "domain",required = false) String domainName,
                                 @RequestParam(value = "request",required = false) String request,
                                 @RequestParam(value = "method",required = false) String method,
                                 @RequestParam(value = "startTime",required = false) String startTime,
                                 @RequestParam(value = "endTime",required = false) String endTime,
                                 HttpServletResponse response) throws Exception, IOException {
        log.info("RecordController#downloadByEsQuery: domainName"+ domainName + ",request:"+request + ",method:"+method);
        recordService.getGetFlowsByEsQuery(domainName,request,method,startTime,endTime,response);
    }

    @ApiOperation(value = "通过Es查询获取日志中的接口并批量添加", notes = "通过Es查询获取日志中的接口流量",produces = "application/json")
    @PostMapping(value = "/moreAdd")
    public ApiResponse moreAdd(@RequestParam(value = "domain",required = false) String domainName,
                                  @RequestParam(value = "request",required = false) String request,
                                  @RequestParam(value = "method",required = false) String method,
                                  @RequestParam(value = "startTime",required = false) String startTime,
                                  @RequestParam(value = "endTime",required = false) String endTime) throws Exception, IOException {
        log.info("RecordController#moreAdd: domainName"+ domainName + ",request:"+request + ",method:"+method);
        return toAjax(recordService.moreAdd(domainName,request,method,startTime,endTime));
    }

    @ApiOperation(value = "获取录制接口结果数据", notes = "获取录制接口结果数据",produces = "application/json")
    @GetMapping(value = "/result")
    public ApiResponse<RecordResult> recordResult(@RequestParam(value = "apiId") Integer apiId,
                                                  @RequestParam(value = "recordId") Integer recordId) throws Exception{
        Result<Object> result = recordService.recordResult(apiId,recordId);
        return new ApiResponse(result);
    }

    @ApiOperation(value = "保存录制接口结果数据", notes = "保存录制接口结果数据",produces = "application/json")
    @PostMapping(value = "/save")
    public ApiResponse<RecordResult> saveRecordResult(@RequestBody RecordResult recordResult) throws Exception{
        Result<Object> result = recordService.saveRecordResult(recordResult);
        return new ApiResponse(result);
    }




}