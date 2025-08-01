package com.tal.wangxiao.conan.admin.service.impl;

import com.google.common.collect.Lists;
import com.tal.wangxiao.conan.admin.cache.AdminCache;
import com.tal.wangxiao.conan.admin.constant.TaskStatus;
import com.tal.wangxiao.conan.admin.service.TaskExecutionService;
import com.tal.wangxiao.conan.common.api.ResponseCode;
import com.tal.wangxiao.conan.common.domain.TaskExecutionInfo;
import com.tal.wangxiao.conan.common.entity.db.Record;
import com.tal.wangxiao.conan.common.entity.db.Task;
import com.tal.wangxiao.conan.common.entity.db.TaskApiRelation;
import com.tal.wangxiao.conan.common.entity.db.TaskExecution;
import com.tal.wangxiao.conan.common.kafaka.*;
import com.tal.wangxiao.conan.common.mapper.TaskExecutionMapper;
import com.tal.wangxiao.conan.common.model.Result;
import com.tal.wangxiao.conan.common.repository.db.*;
import com.tal.wangxiao.conan.common.service.common.AgentCommonService;
import com.tal.wangxiao.conan.common.service.common.KafkaMessageService;
import com.tal.wangxiao.conan.common.utils.task.TaskStatusUtil;
import com.tal.wangxiao.conan.sys.framework.web.service.TokenService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 任务管理服务实现类
 *
 * @author liujinsong
 * @date 2019/6/19
 */
@Service
@Slf4j
public class TaskExecutionServiceImpl implements TaskExecutionService {

    @Resource
    private TaskRepository taskRepository;

    @Resource
    private TaskExecutionRepository taskExecutionRepository;

    @Resource
    private TaskApiRelationRepository taskApiRelationRepository;

    @Resource
    private RecordRepository recordRepository;

    @Resource
    private TokenService tokenService;

    @Resource
    private KafkaTemplate<String, String> producer;

    @Value("${kafka.topic.conan-task-dist}")
    private String topic;

    @Resource
    private AgentCommonService agentCommonService;

    @Resource
    private KafkaMessageService kafkaMessageService;

    @Resource
    private TaskExecutionMapper taskExecutionMapper;


    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Object> initExcAndRecord(Integer taskId, Integer operateBy) throws Exception {
        Task task = new Task();
        if (!Objects.isNull(taskId)) {
            Optional<Task> taskOptional = taskRepository.findById(taskId);
            if (!taskOptional.isPresent()) {
                return new Result(ResponseCode.INVALID_TASK_ID, taskId);
            }
            task = taskOptional.get();
            task.setStatus(TaskStatus.RECORD.getValue());
            task = taskRepository.save(task);
        }
        TaskExecution taskExecution = new TaskExecution();
        taskExecution.setTaskId(taskId);
        taskExecution.setStatus(TaskStatus.RECORD.getValue());
        taskExecution.setCreateBy(operateBy);
        taskExecution.setUpdateBy(operateBy);
        taskExecution.setUpdateAt(LocalDateTime.now());
        taskExecution.setCreateAt(LocalDateTime.now());
        //创建任务执行对象
        TaskExecution newTaskExecution = taskExecutionRepository.saveAndFlush(taskExecution);
        Integer taskExecutionId = newTaskExecution.getId();
        //生成录制ID
        Record newRecord = createRecord(taskId, taskExecutionId, operateBy);
        //根据taskExecution 触发Agent开启录制功能
        if (!Objects.isNull(taskExecutionId)) {
            KafkaTaskData taskData = new KafkaTaskData();
            String agentId = agentCommonService.getAgentId(AdminCache.getEnv());
            taskData.setAgentId(agentId);
            taskData.setRecordId(newRecord.getId());
            taskData.setTaskExecutionId(taskExecutionId);
            log.info("下发录制任务，agentId = {}, task_execution_id = {}, record_id = {}", agentId, taskExecutionId, newRecord.getId());
            KafkaData<KafkaTaskData> kafkaData = new KafkaData<>();
            kafkaData.setType(KafkaType.RECORD);
            kafkaData.setRunEnv(AdminCache.getEnv());
            kafkaData.setData(taskData);
            TaskMessage<KafkaTaskData> taskMessage = new TaskMessage<>();
            taskMessage.setTimestamp(System.currentTimeMillis());
            taskMessage.setData(kafkaData);
            log.info("消息写入,执行环境为: " + AdminCache.getEnv());
            kafkaMessageService.sendKafkaMessage(taskMessage, KafkaTopic.CONAN_TASK_DIST);
            return new Result<>(ResponseCode.SUCCESS, taskMessage);
        } else {
            return new Result<>(ResponseCode.INVALID_TASK_EXECUTION_ID, "创建任务执行记录失败");
        }

    }

    @Override
    public List<TaskExecutionInfo> findTaskExecutionList(TaskExecutionInfo taskExecutionInfo) {
        List<TaskExecutionInfo> result = new ArrayList<>();
        List<TaskExecutionInfo> taskExecutionInfos = taskExecutionMapper.selectTaskExecutionList(taskExecutionInfo);
        // 判断该任务是否已删除，和任务状态是否已经执行过录制回放，如果执行过录制和回放，则不需要移除，如果已经删除了且未执行过录制和回放，则需要移除
        for (TaskExecutionInfo taskExecution : taskExecutionInfos) {
            if (taskExecution.getIsDelete() > 0) {
                // 任务状态为待配置或可录制时，说明任务还未执行
                if (taskExecution.getStatus() == 0 || taskExecution.getStatus() == 10) {
                    continue;
                }
            }
            result.add(taskExecution);
        }
        return result;
    }


    private Record createRecord(Integer taskId, Integer taskExecutionId, Integer operateBy) throws Exception {
        Record record = new Record();
        //计算每个任务的接口个数
        Optional<List<TaskApiRelation>> taskApiRelationList = taskApiRelationRepository.findAllByTaskId(taskId);
        List<Integer> apiList = Lists.newArrayList();
        if(!taskApiRelationList.isPresent()) {
            throw new Exception("该任务下面没有绑定接口，请绑定后再执行录制");
        }
        for (TaskApiRelation taskApiRelation : taskApiRelationList.get()) {
            Integer apiId = taskApiRelation.getApiId();
            apiList.add(apiId);
        }
        Integer apiCount = apiList.size();
        record.setTaskExecutionId(taskExecutionId);
        record.setApiCount(apiCount);
        record.setOperateBy(operateBy);
        record.setStartTime(LocalDateTime.now());
        //recordRepository.save(record);
        return recordRepository.save(record);
    }
}

