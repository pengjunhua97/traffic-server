package com.tal.wangxiao.conan.admin.service.impl;

import com.alibaba.fastjson.JSONObject;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.tal.wangxiao.conan.admin.cache.AdminCache;
import com.tal.wangxiao.conan.admin.service.ReplayService;
import com.tal.wangxiao.conan.common.api.ResponseCode;
import com.tal.wangxiao.conan.common.constant.enums.TaskStatus;
import com.tal.wangxiao.conan.common.converter.ConvertUtil;
import com.tal.wangxiao.conan.common.converter.ReplayConverter;
import com.tal.wangxiao.conan.common.converter.ReplayDetailConverter;
import com.tal.wangxiao.conan.common.entity.db.*;
import com.tal.wangxiao.conan.common.kafaka.*;
import com.tal.wangxiao.conan.common.model.Result;
import com.tal.wangxiao.conan.common.model.vo.ReplayDetailVO;
import com.tal.wangxiao.conan.common.model.vo.ReplayVO;
import com.tal.wangxiao.conan.common.redis.RedisTemplateTool;
import com.tal.wangxiao.conan.common.repository.db.*;
import com.tal.wangxiao.conan.common.service.common.AgentCommonService;
import com.tal.wangxiao.conan.common.service.common.KafkaMessageService;
import com.tal.wangxiao.conan.sys.auth.core.domain.model.LoginUser;
import com.tal.wangxiao.conan.sys.common.utils.ServletUtils;
import com.tal.wangxiao.conan.sys.framework.web.service.TokenService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.transaction.Transactional;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 流量回放服务实现类
 *
 * @author huyaoguo
 * @date 2021/1/4
 **/
@Service
@Slf4j
public class ReplayServiceImpl implements ReplayService {

    @Resource
    private TaskExecutionRepository taskExecutionRepository;

    @Resource
    private RecordRepository recordRepository;

    @Resource
    private ReplayRepository replayRepository;

    @Resource
    private AgentCommonService agentCommonService;

    @Resource
    private RecordDetailRepository recordDetailRepository;

    @Resource
    private ReplayDetailRepository replayDetailRepository;

    @Resource
    private TokenService tokenService;

    @Resource
    private ApiRepository apiRepository;

    @Resource
    private DomainRepository domainRepository;

    @Resource
    private RecordResultRepository recordResultRepository;

    @Resource
    private RedisTemplate<String, String> template;

    @Resource
    private KafkaMessageService kafkaMessageService;

    @Resource
    private RedisTemplateTool redisTemplateTool;

    @Resource
    private DiffRepository diffRepository;

    @Override
    public Result<Object> findReplaysByTaskExecutionId(Integer taskExecutionId) {
        List<Replay> replayList = replayRepository.findByTaskExecutionIdOrderByStartAtDesc(taskExecutionId);
        List<ReplayVO> replayVOList = ConvertUtil.convert2List(replayList, ReplayVO.class, new ReplayConverter());
        boolean isBaseline = false;
        if (!CollectionUtils.isEmpty(replayVOList)) {
            for (ReplayVO replayVO : replayVOList) {
                if (replayVO.getIsBaseline()) {
                    isBaseline = true;
                    break;
                }
            }
            for (ReplayVO replayVO : replayVOList) {
                if (isBaseline) {
                    replayVO.setHasBaseLine(1);
                }else {
                    replayVO.setHasBaseLine(0);
                }
                Optional<Diff> diffOptional = diffRepository.findFirstByTaskExecutionIdAndReplayIdOrderByCreateTimeDesc(taskExecutionId, replayVO.getId());
                if (diffOptional.isPresent()) {
                    replayVO.setDiffed(1);
                }else {
                    replayVO.setDiffed(0);
                }
            }
        }
        return new Result<>(ResponseCode.SUCCESS, replayVOList);
    }

    @Override
    public Integer findDiffIdByReplayId(Integer replayId) {
        Optional<Replay> replayOptional1 = replayRepository.findById(replayId);
        if (!replayOptional1.isPresent()) {
            return 0;
        }
        Optional<Diff> diffOptional = diffRepository.findFirstByTaskExecutionIdAndReplayIdOrderByCreateTimeDesc(replayOptional1.get().getTaskExecutionId(), replayId);
        if (!diffOptional.isPresent()) {
            return 0;
        }
        return diffOptional.get().getId();
    }

    @Override
    public Result<Object> findDetailByReplayId(Integer replayId) {
        List<ReplayDetail> replayDetailList = replayDetailRepository.findByReplayId(replayId);
        if (Objects.isNull(replayDetailList) || replayDetailList.isEmpty()) {
            return new Result<>(ResponseCode.REPLAY_DETAIL_NOT_FOUND, "replay_id = " + replayId);
        }
        List<ReplayDetailVO> replayDetailVOList = ConvertUtil.convert2List(replayDetailList, ReplayDetailVO.class, new ReplayDetailConverter());
        return new Result<>(ResponseCode.SUCCESS, replayDetailVOList);
    }

    @Override
    @Transactional(rollbackOn = Exception.class)
    public Result<Object> startReplay(Integer taskExecutionId, String replayEnv, Integer replayType) {
        Optional<TaskExecution> taskExecutionOptional = taskExecutionRepository.findById(taskExecutionId);
        if (!taskExecutionOptional.isPresent()) {
            return new Result<>(ResponseCode.INVALID_TASK_EXECUTION_ID, "taskExecutionId(" + taskExecutionId + ") invalid");
        }
        TaskExecution taskExecution = taskExecutionOptional.get();
        if (taskExecution.getStatus().equals(TaskStatus.READY.getValue()) ||
                taskExecution.getStatus().equals(TaskStatus.RECORD.getValue()) ||
                taskExecution.getStatus().equals(TaskStatus.RECORD_FAIL.getValue()) ||
                taskExecution.getStatus().equals(TaskStatus.REPLAY.getValue()) ||
                taskExecution.getStatus().equals(TaskStatus.DIFF.getValue())) {
            return new Result<>(ResponseCode.INVALID_STATUS_FOR_REPLAY, taskExecution.getStatus().toString() + " can't replay");
        }
        Optional<Record> recordOptional = recordRepository.findFirstByTaskExecutionId(taskExecutionId);
        if (!recordOptional.isPresent()) {
            return new Result<>(ResponseCode.RECORD_NOT_FOUND, "taskExecutionId(" + taskExecutionId + ") have not record data");
        }
        LoginUser loginUser = tokenService.getLoginUser(ServletUtils.getRequest());
        log.info("start replay by userId = " + loginUser.getUser().getUserId());
        LocalDateTime operateTime = LocalDateTime.now();
        Replay replay = new Replay();
        replay.setTaskExecutionId(taskExecutionId);
        replay.setRecordId(recordOptional.get().getId());
        replay.setOperateBy(loginUser.getUser().getUserId().intValue());
        replay.setStartAt(operateTime);
        replay.setReplayType(replayType);
        replay.setReplayEnv(replayEnv);
        replay.setIsBaseline(false);
        replay.setSuccessRate((double) 0);
        Replay newReplay = replayRepository.save(replay);
        taskExecution.setUpdateAt(operateTime);
        taskExecution.setUpdateBy(loginUser.getUser().getUserId().intValue());
        taskExecutionRepository.save(taskExecution);

        List<RecordDetail> recordDetailList = recordDetailRepository.findByRecordId(recordOptional.get().getId());
        if (Objects.isNull(recordDetailList) || 0 == recordDetailList.size()) {
            return new Result<>(ResponseCode.RECORD_DETAIL_NOT_FOUND, "recordId(" + recordOptional.get().getId() + ")");
        }
        for (RecordDetail recordDetail : recordDetailList) {
            //如果某一接口没有录制到流量则不能回放
            if (Objects.isNull(recordDetail.getActualCount()) || 0 == recordDetail.getActualCount()) {
                continue;
            }
            ReplayDetail replayDetail = new ReplayDetail();
            replayDetail.setApiId(recordDetail.getApiId());
            // 录制的实际条数是期望的回放条数
            replayDetail.setExpectCount(recordDetail.getActualCount());
            replayDetail.setReplayId(newReplay.getId());
            replayDetailRepository.save(replayDetail);
        }
        //下发回放任务
        KafkaTaskData taskData = new KafkaTaskData();
        String agentId = agentCommonService.getAgentId(replayEnv);//AdminCache.getEnv()); //zc
        taskData.setAgentId(agentId);
        taskData.setRecordId(recordOptional.get().getId());
        taskData.setReplayId(newReplay.getId());
        taskData.setTaskExecutionId(taskExecutionId);
        log.info("下发回放任务，agentId = {}, task_execution_id = {}, record_id = {}, replay_id = {}", agentId, taskExecutionId, recordOptional.get().getId(), newReplay.getId());
        KafkaData<KafkaTaskData> kafkaData = new KafkaData<>();
        kafkaData.setType(KafkaType.REPLAY);
        kafkaData.setRunEnv(replayEnv);//AdminCache.getEnv());//zc
        kafkaData.setData(taskData);
        TaskMessage<KafkaTaskData> taskMessage = new TaskMessage<>();
        taskMessage.setTimestamp(System.currentTimeMillis());
        taskMessage.setData(kafkaData);
        log.info("消息写入,执行环境为: " + replayEnv);//AdminCache.getEnv());//zc
        kafkaMessageService.sendKafkaMessage(taskMessage, KafkaTopic.CONAN_TASK_DIST);
        return new Result<>(ResponseCode.SUCCESS, taskMessage);
    }

    @Override
    public Result<Object> findOneApiDetailByReplayIdAndApiId(Integer replayId, Integer apiId) {
        Map<String, Object> resultMap = new HashMap<String, Object>();
        Optional<Replay> replayOptional = replayRepository.findById(replayId);
        if (!replayOptional.isPresent()) {
            return new Result<>(ResponseCode.INVALID_REPLAY_ID, "找不到回放记录，无效的replayId");
        }
        Integer recordId = replayOptional.get().getRecordId();
        Optional<Api> apiOptional = apiRepository.findById(apiId);
        if (!apiOptional.isPresent()) {
            return new Result<>(ResponseCode.INVALID_API_ID, "找不到该接口详情，api_id=" + apiId);
        }
        Optional<Domain> domainOptional = domainRepository.findById(apiOptional.get().getDomainId());
        if (!domainOptional.isPresent()) {
            return new Result<>(ResponseCode.INVALID_DOMAIN_ID, "找不到该接口的域名，api_id=" + apiId);
        }
        resultMap.put("domain", domainOptional.get().getName());
        resultMap.put("api", apiOptional.get().getName());
        Optional<ReplayDetail> replayDetailOptional = replayDetailRepository.findByReplayIdAndApiId(replayId, apiId);
        if (!replayDetailOptional.isPresent()) {
            return new Result<>(ResponseCode.INVALID_DOMAIN_ID, "找不到该接口的回放详情，api_id=" + apiId + "，replay_id=" + replayId);
        }
        Integer expectCount = replayDetailOptional.get().getExpectCount();
        if(expectCount == 0) {
            expectCount = 1;
        }
        if (!Objects.isNull(replayDetailOptional.get().getActualCount())) {
            resultMap.put("successRate", (replayDetailOptional.get().getActualCount() * 100 /expectCount) + "%");
        }
        //获取同一 record_id 相同api的request_ID set去重
        List<RecordResult> findByRecordIdAndApiId = recordResultRepository.findByRecordIdAndApiId(recordId, apiId);
        Set<String> apiRequestSet = new HashSet<String>();
        for (RecordResult requestResult : findByRecordIdAndApiId) {
            apiRequestSet.add(requestResult.getRequestId());
        }
        String response = "";
        String requestBody = "";
        List<Map<String, Object>> apiDetailList = new ArrayList<>();
        for (String requestId : apiRequestSet) {
            Map<String, Object> oneApiDetailMap = new HashMap<>();
            try {
                response = template.opsForValue().get(requestId + "-" + recordId + "-" + replayId + "-" + apiId);
                requestBody = template.opsForValue().get(requestId + "-" + recordId + "-" + replayId + "-" + apiId + "-body");
            } catch (Exception e) {
                return new Result<>(ResponseCode.INVALID_REDIS_KEY, requestId + "-" + recordId + "-" + replayId + "-" + apiId);
            }
            Map<String, String> splitStr = splitStr(requestBody);
            System.out.println("转化前的json对象：" + response);
            // 如果字符串可能被多次转义
            JSONObject jsonObject = safeParse(response);
            System.out.println("转化后的json对象：" + jsonObject);
            oneApiDetailMap.put("response", jsonObject);
            oneApiDetailMap.put("requestId", splitStr.get("requestId"));
            oneApiDetailMap.put("request_body_record", splitStr.get("request_body_record"));
            oneApiDetailMap.put("request_body", splitStr.get("request_body"));
            apiDetailList.add(oneApiDetailMap);
        }
        resultMap.put("replay_detail", apiDetailList);
        return new Result<>(ResponseCode.SUCCESS, resultMap);
    }

    public JSONObject safeParse(String jsonStr) {
        try {
            // 先尝试直接解析
            return JSONObject.parseObject(jsonStr);
        } catch (Exception e) {
            try {
                // 处理可能的多重转义情况
                if (jsonStr.startsWith("\"") && jsonStr.endsWith("\"")) {
                    String unescaped = jsonStr.substring(1, jsonStr.length()-1)
                            .replace("\\\"", "\"");
                    return JSONObject.parseObject(unescaped);
                }
                throw e;
            } catch (Exception ex) {
                throw new RuntimeException("JSON解析失败: " + ex.getMessage());
            }
        }
    }

    public Map<String,String> splitStr(String str) {
        System.out.println("响应数据：" + str);
        Map<String,String> map = new HashMap<>();
        if (StringUtils.isNotBlank(str)) {
            // 定义正则表达式
            Pattern pattern = Pattern.compile("\"requestId=([^原]+)原body=(.*)新body=(.*)\"$");
            Matcher matcher = pattern.matcher(str);

            String requestId = null;
            String oldBody = null;
            String newBody = null;
            if (matcher.find()) {
                requestId = matcher.group(1);
                oldBody = matcher.group(2).isEmpty() ? null : matcher.group(2);
                newBody = matcher.group(3).isEmpty() ? null : matcher.group(3);

                System.out.println("requestId: " + requestId);
                System.out.println("原body: " + oldBody);
                System.out.println("新body: " + newBody);
            }
            map.put("requestId",requestId);
            map.put("request_body_record",oldBody);
            map.put("request_body",newBody);
        }
        return map;
    }

    @Override
    public Result<String> findLogByReplayId(Integer replayId) {
        Optional<Replay> replayOptional = replayRepository.findById(replayId);
        if (!replayOptional.isPresent()) {
            return new Result<>(ResponseCode.INVALID_REPLAY_ID, "该回放ID不存在");
        }
        String res = template.opsForValue().get("replayLog=" + replayId);
        return new Result<>(ResponseCode.SUCCESS, res);
    }

    @Override
    public Result<Object> findReplayProgress(Integer replayId) {
        try {
            return new Result<>(ResponseCode.SUCCESS, redisTemplateTool.getReplayProgress(replayId));
        } catch (Exception e) {
            return new Result<>(ResponseCode.REDIS_EXCEPTION, "获取回放进度异常：" + e.getMessage());
        }
    }

    @Override
    public Result<String> setBaseLine(Integer replayId) {
        Optional<Replay> replayOptional = replayRepository.findById(replayId);

        if (!replayOptional.isPresent()) {
            return new Result<>(ResponseCode.INVALID_REPLAY_ID, "找不到对应的回放记录");
        }
        Replay replay = replayOptional.get();
        Integer taskExecutionId = replay.getTaskExecutionId();
        List<Replay> replays = replayRepository.findAllByTaskExecutionIdAndIsBaseline(taskExecutionId, true);
        for (Replay replay1 : replays) {
            replay1.setIsBaseline(false);
        }
        replayRepository.saveAll(replays);
        replay.setIsBaseline(true);
        replayRepository.save(replay);
        return new Result<>(ResponseCode.SUCCESS, "设置成功");
    }


}
