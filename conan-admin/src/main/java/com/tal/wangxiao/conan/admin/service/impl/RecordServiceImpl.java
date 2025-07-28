package com.tal.wangxiao.conan.admin.service.impl;

import com.google.common.collect.Lists;
//import com.sun.jndi.toolkit.url.UrlUtil;
import com.tal.wangxiao.conan.admin.constant.HttpMethod;
import com.tal.wangxiao.conan.admin.service.RecordService;
import com.tal.wangxiao.conan.common.api.ResponseCode;
import com.tal.wangxiao.conan.common.constant.enums.HttpMethodConstants;
import com.tal.wangxiao.conan.common.domain.ApiInfo;
import com.tal.wangxiao.conan.common.entity.db.*;
import com.tal.wangxiao.conan.common.exception.BaseException;
import com.tal.wangxiao.conan.common.exception.replay.ReplayException;
import com.tal.wangxiao.conan.common.model.Result;
import com.tal.wangxiao.conan.common.model.bo.RecordDetailInfo;
import com.tal.wangxiao.conan.common.model.bo.RecordInfo;
import com.tal.wangxiao.conan.common.redis.RedisTemplateTool;
import com.tal.wangxiao.conan.common.repository.db.*;
import com.tal.wangxiao.conan.common.service.common.ElasticCommonService;
import com.tal.wangxiao.conan.common.utils.DownloadFileUtil;
import com.tal.wangxiao.conan.sys.auth.core.domain.model.LoginUser;
import com.tal.wangxiao.conan.sys.common.utils.ServletUtils;
import com.tal.wangxiao.conan.sys.framework.web.service.TokenService;
import com.tal.wangxiao.conan.utils.enumutils.EnumUtil;
import com.tal.wangxiao.conan.utils.es.EsUtils;
import com.tal.wangxiao.conan.utils.excel.ExcelHanderUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.elasticsearch.action.search.*;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.RangeQueryBuilder;
import org.elasticsearch.search.Scroll;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.SortOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ResourceUtils;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * @author liujinsong
 * @date 2020/1/14
 */
@Service
@Slf4j
public class RecordServiceImpl implements RecordService{
    @Resource
    private RecordRepository recordRepository;

    @Resource
    private RedisTemplateTool redisTemplateTool;

    @Resource
    private UserRepository userRepository;

    @Resource
    private TaskRepository taskRepository;

    @Resource
    private DomainRepository domainRepository;

    @Resource
    private ApiRepository apiRepository;

    @Resource
    private ElasticCommonService elasticCommonService;

    @Resource
    private RedisTemplate<String, String> template;

    @Resource
    private DepartmentRepository departmentRepository;

    @Resource
    private TaskExecutionRepository taskExecutionRepository;

    @Resource
    private RecordDetailRepository recordDetailRepository;

    @Resource
    private EsConditionSettingRepository esConditionSettingRepository;

    @Resource
    private RecordResultRepository recordResultRepository;

    @Autowired
    private TokenService tokenService;

    @Value("${file.upload.path}")
    private String filePath;


    @Override
    public Result<String> findRecordProgress(Integer taskExecutionId) {
        Optional<Record> recordOptional = recordRepository.findFirstByTaskExecutionId(taskExecutionId);
        if (!recordOptional.isPresent()) {
            log.error("该任务执行ID没有执行记录:" + taskExecutionId);
            return new Result<>(ResponseCode.INVALID_TASK_EXECUTION_ID, "该任务执行ID没有执行记录");
        }
        try {
            return new Result<>(ResponseCode.SUCCESS, redisTemplateTool.getRecordProgress(recordOptional.get().getId()));
        } catch (Exception e) {
            log.error("taskExecutionId={}获取录制进度异常:{}", taskExecutionId, e.getMessage());
            return new Result<>(ResponseCode.REDIS_EXCEPTION, "获取录制进度异常:" + e.getMessage());

        }
    }

    @Override
    public Result<Object> findByTaskExecutionId(Integer taskExecutionId) {
        RecordInfo recordInfo = new RecordInfo();
        List<RecordDetailInfo> recordDetailInfoList = Lists.newArrayList();
        recordInfo.setRecordDetailInfoList(recordDetailInfoList);
        recordInfo.setTaskExecutionId(taskExecutionId);

        Optional<Record> recordOptional = recordRepository.findFirstByTaskExecutionId(taskExecutionId);
        if (!recordOptional.isPresent()) {
            return new Result<>(ResponseCode.INVALID_TASK_EXECUTION_ID, "没有找到录制实体");
        }
        Record record = recordOptional.get();
        recordInfo.setRecordId(record.getId());
        recordInfo.setApiCount(record.getApiCount());
        recordInfo.setStartAt(record.getStartTime());
        recordInfo.setEndAt(record.getEndTime());
        recordInfo.setSuccessRate(record.getSuccessRate());

        Optional<User> userOptional = userRepository.findById(record.getOperateBy());
        if (!userOptional.isPresent()) {
            return new Result<>(ResponseCode.INVALID_USER_ID, "没有找到有效的录制人信息");
        }
        recordInfo.setOperateBy(userOptional.get().getUserName());
        Optional<TaskExecution> taskExecutionOptional = taskExecutionRepository.findById(taskExecutionId);
        if (!taskExecutionOptional.isPresent()) {
            return new Result<>(ResponseCode.INVALID_TASK_EXECUTION_ID, "该任务执行ID无效，找不到有关task_execution_id=" + taskExecutionId + "的执行信息");

        }
        Optional<Task> taskOptional = taskRepository.findById(taskExecutionOptional.get().getTaskId());
        if (!taskOptional.isPresent()) {
            return new Result<>(ResponseCode.INVALID_TASK_ID, "未找到任务实体，task_execution_id = " + taskExecutionId + ", task_id = " + taskExecutionOptional.get().getTaskId());
        }
        recordInfo.setTaskId(taskOptional.get().getId());
        recordInfo.setTaskName(taskOptional.get().getName());

//        Optional<Department> departmentOptional = departmentRepository.findById(taskOptional.get().getDepartmentId());
//        if (!departmentOptional.isPresent()) {
//            return new Result<>(ResponseCode.INVALID_DEPARTMENT_ID, "未找到部门实体：task_id = " + taskOptional.get().getId() + ", department_id = " + taskOptional.get().getDepartmentId());
//        }
//        recordInfo.setDepartmentId(departmentOptional.get().getId());
//        recordInfo.setDepartment(departmentOptional.get().getDeptName());

        List<RecordDetail> recordDetailList = recordDetailRepository.findByRecordId(record.getId());
        if (recordDetailList.isEmpty()) {
            return new Result<>(ResponseCode.INVALID_RECORD_ID, "未找到录制详情实体：record_id = " + record.getId());
        }
        for (RecordDetail recordDetail : recordDetailList) {
            RecordDetailInfo recordDetailInfo = new RecordDetailInfo();
            Optional<Api> apiOptional = apiRepository.findById(recordDetail.getApiId());
            if (!apiOptional.isPresent()) {
                return new Result<>(ResponseCode.INVALID_API_ID, "未找到接口实体：api_id = " + recordDetail.getApiId() + ", record_id = " + recordDetail.getRecordId());
            }
            recordDetailInfo.setApiId(apiOptional.get().getId());
            recordDetailInfo.setApiName(apiOptional.get().getName());
            recordDetailInfo.setDomainId(apiOptional.get().getDomainId());
            recordDetailInfo.setApiMethod(EnumUtil.getByField(HttpMethod.class, "getValue", String.valueOf(apiOptional.get().getMethod())).getLabel());
            recordDetailInfo.setRecordableCount(apiOptional.get().getRecordableCount());
            int expectCount =recordDetail.getExpectCount();
            if(recordDetail.getExpectCount() == 0 ) {
                expectCount = 5;
            }
            recordDetailInfo.setExpectCount(expectCount);
            Integer actualCount = recordDetail.getActualCount();
            if (Objects.isNull(actualCount)) {
                actualCount = 5;
            }
            recordDetailInfo.setActualCount(actualCount);
            Integer successRate = (actualCount * 100) / expectCount;
            recordDetailInfo.setSuccessRate(successRate + "%");
            Optional<Domain> domainOptional = domainRepository.findById(apiOptional.get().getDomainId());
            if (!domainOptional.isPresent()) {
                return new Result<>(ResponseCode.INVALID_API_ID, "未找到域名实体：domain_id = " + apiOptional.get().getDomainId() + ", api_id = " + apiOptional.get().getId());
            }
            recordDetailInfo.setDomainName(domainOptional.get().getName());
            recordDetailInfoList.add(recordDetailInfo);
        }

        return new Result<>(ResponseCode.SUCCESS, recordInfo);
    }

    @Override
    public Result<Object> findLogByExecutionId(Integer taskExecutionId) {
        Optional<Record> recordOptional = recordRepository.findFirstByTaskExecutionId(taskExecutionId);
        if (!recordOptional.isPresent()) {
            return new Result<>(ResponseCode.INVALID_TASK_EXECUTION_ID, "该任务执行ID没有执行记录");
        }
        String res = template.opsForValue().get("recordLog=" + recordOptional.get().getId());
        return new Result<>(ResponseCode.SUCCESS, res);
    }

    @Override
    public void getGetFlowsByDomain(String domainName, HttpServletResponse response) throws Exception{
        Map<String,Integer> apiMap = new HashMap<>();
        //根据域名获取es mapping设置
        String domainKeyword = "";
        String apiKeyWord = "";
        String methodKeyword = "";
        Optional<EsConditionSetting> esConditionSetting = esConditionSettingRepository.findByDomainId(domainRepository.findByName(domainName).get().getId());
        if (esConditionSetting.isPresent()) {
            domainKeyword = esConditionSetting.get().getDomain();
            apiKeyWord = esConditionSetting.get().getApi();
            methodKeyword = esConditionSetting.get().getMethod();
        } else {
            log.info("域名" + domainName + "无es mapping信息配置");
            throw new BaseException("域名" + domainName + "无es mapping信息配置");
        }
        QueryBuilder shouldQuery = QueryBuilders.boolQuery()
                .should(QueryBuilders.termQuery(domainKeyword+".keyword", domainName));
        BoolQueryBuilder queryBuilder = QueryBuilders.boolQuery()
                .must(shouldQuery);
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
        log.info(queryBuilder.toString());
        sourceBuilder.query(queryBuilder);
        sourceBuilder.sort("@timestamp", SortOrder.DESC);
        sourceBuilder.size(500);
        Scroll scroll = new Scroll(TimeValue.timeValueMinutes(10L));
        SearchRequest searchRequest = new SearchRequest();
        searchRequest.source(sourceBuilder).scroll(scroll).indices();
        //根据域名获取对应es配置信息
        RestHighLevelClient restHighLevelClient = elasticCommonService.getRestHighLevelClient(domainName);
        SearchResponse searchResponse = null;
        long totalElements = 0;
        try {
            searchResponse = restHighLevelClient.search(searchRequest, RequestOptions.DEFAULT);
        } catch (Exception e) {
            log.error("日志查询失败" + e);
        }
        String scrollId = searchResponse.getScrollId();
        totalElements = searchResponse.getHits().totalHits;
        int total = (int) totalElements;
        log.info("查询到总流量" + totalElements + "条");
        int length = searchResponse.getHits().getHits().length;
        log.info("查询到总流量 length" + length + "");
        int page = total / 10000;//计算总页数,每次搜索数量为分片数*设置的size大小
        page = page + 1;
        int scanElements = 0;
        //控制单个接口录制条数
        for (int i = 0; i < page; i++) {
            if (i != 0) {
                SearchScrollRequest scrollRequest = new SearchScrollRequest(searchResponse.getScrollId());
                scroll = new Scroll(TimeValue.timeValueMinutes(10L));
                scrollRequest.scroll(scroll);
                try {
                    searchResponse = restHighLevelClient.scroll(scrollRequest, RequestOptions.DEFAULT);
                } catch (Exception e) {
                    log.error("日志查询失败");
                }
            }
            //控制录制总流量数
            scanElements+=length;
            if(scanElements>=1000000){
                log.info("扫描流量超过100W，即将退出扫描");
                break;
            }
            // 业务逻辑
            for (SearchHit at : searchResponse.getHits()) {
                String method = at.getSourceAsMap().get(methodKeyword).toString();
                String url = EsUtils.getUrlByRequest(at.getSourceAsMap().get(apiKeyWord).toString());
                //根据方法名判断
                if ("OPTIONS".equals(method)||"HEAD".equals(method)) {
                    //再次过滤OPTIONS HEAD请求
                    continue;
                }
                //根据url判断
                if (EsUtils.shouldIgnore(url)||url==null||url.contains("null")||url.contains(".")) {
                    //再次过滤静态资源请求
                    continue;
                }
                url = EsUtils.replacePathParam(url).replaceAll("[&?!].*","");
                url = method + " "+ url;
                //url= url.replaceAll("null","{pp}");
                if(!apiMap.containsKey(url)){
                    apiMap.put(url,0);
                }else{
                    //控制单接口录制条数
                    if (apiMap.get(url) > 100) {
                        continue;
                    }
                    apiMap.put(url,apiMap.get(url)+1);
                }
            }
        }
        try {
            saveExcelApiList(apiMap,domainName,response);
        }catch (Exception e){
            log.error("存储Excel异常");
            e.printStackTrace();
            throw new BaseException("存储Excel异常");
        }
        //清除scroll
        ClearScrollRequest clearScrollRequest = new ClearScrollRequest();
        clearScrollRequest.addScrollId(scrollId);//也可以选择setScrollIds()将多个scrollId一起使用
        ClearScrollResponse clearScrollResponse = null;
        try {
            clearScrollResponse = restHighLevelClient.clearScroll(clearScrollRequest, RequestOptions.DEFAULT);
        } catch (IOException e) {
            e.printStackTrace();
        }
        boolean succeeded = clearScrollResponse.isSucceeded();
        log.info("ES succeeded = " + succeeded);
    }

    @Override
    public void getGetFlowsByEsQuery(String domainName,String request,String requestMethod,String startTime,String endTime, HttpServletResponse response) {
        Map<String,Integer> apiMap = new HashMap<>();
        //根据域名获取es mapping设置
        String domainKeyword = "";
        String apiKeyWord = "";
        String methodKeyword = "";
        Optional<EsConditionSetting> esConditionSetting = esConditionSettingRepository.findByDomainId(domainRepository.findByName(domainName).get().getId());
        if (esConditionSetting.isPresent()) {
            domainKeyword = esConditionSetting.get().getDomain();
            apiKeyWord = esConditionSetting.get().getApi();
            methodKeyword = esConditionSetting.get().getMethod();
        } else {
            log.info("域名" + domainName + "无es mapping信息配置");
            throw new BaseException("域名" + domainName + "无es mapping信息配置");
        }
        QueryBuilder shouldQuery = QueryBuilders.boolQuery()
                .should(QueryBuilders.termQuery(domainKeyword+".keyword", domainName));
        QueryBuilder queryBuilder1 = null;
        // 根据请求方式GET/POST/PUT/DELETE等过滤
        if (StringUtils.isNotBlank(requestMethod)) {
            String reqMethod = EnumUtil.getByField(HttpMethodConstants.class, "getValue", requestMethod).getLabel();
            if (StringUtils.isNotBlank(reqMethod)) {
                queryBuilder1 = QueryBuilders.boolQuery()
                        .should(QueryBuilders.termQuery(methodKeyword + ".keyword", reqMethod));
            }
        }
        BoolQueryBuilder queryBuilder2 = null;
        // 根据请求request过滤
        if (StringUtils.isNotBlank(request)) {
            queryBuilder2 = QueryBuilders.boolQuery()
                    .must(QueryBuilders.wildcardQuery(apiKeyWord + ".keyword", "*" + request + "*"));
        }
        RangeQueryBuilder rangeQuery = null;
        // 根据开始时间结束时间过滤
        if (StringUtils.isNotBlank(startTime) && StringUtils.isNotBlank(endTime)) {
            // 将时间转换成Es中的时间格式
            DateTimeFormatter inputFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            LocalDateTime startLocalDateTime = LocalDateTime.parse(startTime, inputFormatter);
            LocalDateTime endLocalDateTime = LocalDateTime.parse(endTime, inputFormatter);
            ZonedDateTime start = startLocalDateTime.atZone(ZoneId.of("UTC")); // 添加UTC时区
            ZonedDateTime end = endLocalDateTime.atZone(ZoneId.of("UTC")); // 添加UTC时区
            rangeQuery = QueryBuilders.rangeQuery("@timestamp")
                   .gte(start)  // 大于等于开始时间
                  .lte(end);   // 小于等于结束时间
        }
        BoolQueryBuilder boolQuery = QueryBuilders.boolQuery()
                .must(shouldQuery);
        if (queryBuilder1 != null) {
            boolQuery.must(queryBuilder1);
        }
        if (queryBuilder2 != null) {
            boolQuery.must(queryBuilder2);
        }
        if (rangeQuery != null) {
            boolQuery.must(rangeQuery);
        }
        log.info("Es查询条件" + boolQuery.toString());
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
        sourceBuilder.query(boolQuery);
        sourceBuilder.sort("@timestamp", SortOrder.DESC);
        sourceBuilder.size(500);
        Scroll scroll = new Scroll(TimeValue.timeValueMinutes(10L));
        SearchRequest searchRequest = new SearchRequest();
        searchRequest.source(sourceBuilder).scroll(scroll).indices(esConditionSetting.get().getIndexName());
        //根据域名获取对应es配置信息
        RestHighLevelClient restHighLevelClient = elasticCommonService.getRestHighLevelClient(domainName);
        SearchResponse searchResponse = null;
        long totalElements = 0;
        try {
            searchResponse = restHighLevelClient.search(searchRequest, RequestOptions.DEFAULT);
        } catch (Exception e) {
            log.error("日志查询失败" + e);
        }
        String scrollId = searchResponse.getScrollId();
        totalElements = searchResponse.getHits().totalHits;
        int total = (int) totalElements;
        log.info("查询到总流量" + totalElements + "条");
        int length = searchResponse.getHits().getHits().length;
        log.info("查询到总流量 length" + length + "");
        int page = total / 10000;//计算总页数,每次搜索数量为分片数*设置的size大小
        page = page + 1;
        int scanElements = 0;
        //控制单个接口录制条数
        for (int i = 0; i < page; i++) {
            if (i != 0) {
                SearchScrollRequest scrollRequest = new SearchScrollRequest(searchResponse.getScrollId());
                scroll = new Scroll(TimeValue.timeValueMinutes(10L));
                scrollRequest.scroll(scroll);
                try {
                    searchResponse = restHighLevelClient.scroll(scrollRequest, RequestOptions.DEFAULT);
                } catch (Exception e) {
                    log.error("日志查询失败");
                }
            }
            //控制录制总流量数
            scanElements+=length;
            if(scanElements>=1000000){
                log.info("扫描流量超过100W，即将退出扫描");
                break;
            }
            SearchHit[] hits = searchResponse.getHits().getHits();
            log.info("查询到总流量 length" + hits.length + "");
            // 业务逻辑
            for (SearchHit at : searchResponse.getHits()) {
                String method = at.getSourceAsMap().get(methodKeyword).toString();
                String url = EsUtils.getUrlByRequest(at.getSourceAsMap().get(apiKeyWord).toString());
                //根据方法名判断
                if ("OPTIONS".equals(method)||"HEAD".equals(method)) {
                    //再次过滤OPTIONS HEAD请求
                    continue;
                }
                //根据url判断
                if (EsUtils.shouldIgnore(url)||url==null||url.contains("null")||url.contains(".")) {
                    //再次过滤静态资源请求
                    continue;
                }
                url = EsUtils.replacePathParam(url).replaceAll("[&?!].*","");
                url = method + " "+ url;
                //url= url.replaceAll("null","{pp}");
                if(!apiMap.containsKey(url)){
                    apiMap.put(url,0);
                }else{
                    //控制单接口录制条数
                    if (apiMap.get(url) > 100) {
                        continue;
                    }
                    apiMap.put(url,apiMap.get(url)+1);
                }
            }
            try {
                saveExcelApiList(apiMap,domainName,response);
            }catch (Exception e){
                log.error("存储Excel异常");
                e.printStackTrace();
                throw new BaseException("存储Excel异常");
            }
            //清除scroll
            ClearScrollRequest clearScrollRequest = new ClearScrollRequest();
            clearScrollRequest.addScrollId(scrollId);//也可以选择setScrollIds()将多个scrollId一起使用
            ClearScrollResponse clearScrollResponse = null;
            try {
                clearScrollResponse = restHighLevelClient.clearScroll(clearScrollRequest, RequestOptions.DEFAULT);
            } catch (IOException e) {
                e.printStackTrace();
            }
            boolean succeeded = clearScrollResponse.isSucceeded();
            log.info("ES succeeded = " + succeeded);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int moreAdd(String domainName, String request, String requestMethod, String startTime, String endTime) throws ParseException {
        LoginUser loginUser = tokenService.getLoginUser(ServletUtils.getRequest());
        Optional<Domain> domainOptional = domainRepository.findByName(domainName);
        Integer domainId = null;
        if (!domainOptional.isPresent()) {
            Domain domain1 = new Domain();
            domain1.setName(domainName);
            domain1.setCreateBy(loginUser.getUser().getUserId().intValue());
            domain1.setCreateAt(LocalDateTime.now());
            domain1.setOnline(true);
            domain1.setIsAuth(false);
            Domain newDomain = domainRepository.save(domain1);
            domainId = newDomain.getId();
            log.info("新增域名 name = " + domainName);
        } else {
            domainId = domainOptional.get().getId();
        }
        Map<String,Integer> apiMap = new HashMap<>();
        boolean succeeded = false;
        //根据域名获取es mapping设置
        String domainKeyword = "";
        String apiKeyWord = "";
        String methodKeyword = "";
        String requestBody = "";
        Optional<EsConditionSetting> esConditionSetting = esConditionSettingRepository.findByDomainId(domainRepository.findByName(domainName).get().getId());
        if (esConditionSetting.isPresent()) {
            domainKeyword = esConditionSetting.get().getDomain();
            apiKeyWord = esConditionSetting.get().getApi();
            methodKeyword = esConditionSetting.get().getMethod();
            requestBody = esConditionSetting.get().getRequestBody();
        } else {
            log.info("域名" + domainName + "无es mapping信息配置");
            throw new BaseException("域名" + domainName + "无es mapping信息配置");
        }
        QueryBuilder shouldQuery = QueryBuilders.boolQuery()
                .should(QueryBuilders.termQuery(domainKeyword+".keyword", domainName));
        QueryBuilder queryBuilder1 = null;
        // 根据请求方式GET/POST/PUT/DELETE等过滤
        if (StringUtils.isNotBlank(requestMethod)) {
            String reqMethod = EnumUtil.getByField(HttpMethodConstants.class, "getValue", requestMethod).getLabel();
            if (StringUtils.isNotBlank(reqMethod)) {
                queryBuilder1 = QueryBuilders.boolQuery()
                        .should(QueryBuilders.termQuery(methodKeyword + ".keyword", reqMethod));
            }
        }
        BoolQueryBuilder queryBuilder2 = null;
        // 根据请求request过滤
        if (StringUtils.isNotBlank(request)) {
            queryBuilder2 = QueryBuilders.boolQuery()
                    .must(QueryBuilders.wildcardQuery(apiKeyWord + ".keyword", "*" + request + "*"));
        }
        RangeQueryBuilder rangeQuery = null;
        // 根据开始时间结束时间过滤
        if (StringUtils.isNotBlank(startTime) && StringUtils.isNotBlank(endTime)) {
            // 将时间转换成Es中的时间格式
            DateTimeFormatter inputFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            LocalDateTime startLocalDateTime = LocalDateTime.parse(startTime, inputFormatter);
            LocalDateTime endLocalDateTime = LocalDateTime.parse(endTime, inputFormatter);
            ZonedDateTime start = startLocalDateTime.atZone(ZoneId.of("UTC")); // 添加UTC时区
            ZonedDateTime end = endLocalDateTime.atZone(ZoneId.of("UTC")); // 添加UTC时区
            rangeQuery = QueryBuilders.rangeQuery("@timestamp")
                    .gte(start)  // 大于等于开始时间
                    .lte(end);   // 小于等于结束时间
        }
        BoolQueryBuilder boolQuery = QueryBuilders.boolQuery()
                .must(shouldQuery);
        if (queryBuilder1 != null) {
            boolQuery.must(queryBuilder1);
        }
        if (queryBuilder2 != null) {
            boolQuery.must(queryBuilder2);
        }
        if (rangeQuery != null) {
            boolQuery.must(rangeQuery);
        }
        log.info("Es查询条件" + boolQuery.toString());
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
        sourceBuilder.query(boolQuery);
        sourceBuilder.sort("@timestamp", SortOrder.DESC);
        sourceBuilder.size(500);
        Scroll scroll = new Scroll(TimeValue.timeValueMinutes(10L));
        SearchRequest searchRequest = new SearchRequest();
        searchRequest.source(sourceBuilder).scroll(scroll).indices(esConditionSetting.get().getIndexName());
        //根据域名获取对应es配置信息
        RestHighLevelClient restHighLevelClient = elasticCommonService.getRestHighLevelClient(domainName);
        SearchResponse searchResponse = null;
        long totalElements = 0;
        try {
            searchResponse = restHighLevelClient.search(searchRequest, RequestOptions.DEFAULT);
        } catch (Exception e) {
            log.error("日志查询失败" + e);
            throw new BaseException("日志查询失败,失败信息：" + e.getMessage());
        }
        String scrollId = searchResponse.getScrollId();
        totalElements = searchResponse.getHits().totalHits;
        int total = (int) totalElements;
        log.info("查询到总流量" + totalElements + "条");
        int length = searchResponse.getHits().getHits().length;
        log.info("查询到总流量 length" + length + "");
        int page = total / 10000;//计算总页数,每次搜索数量为分片数*设置的size大小
        page = page + 1;
        int scanElements = 0;
        //控制单个接口录制条数
        for (int i = 0; i < page; i++) {
            if (i != 0) {
                SearchScrollRequest scrollRequest = new SearchScrollRequest(searchResponse.getScrollId());
                scroll = new Scroll(TimeValue.timeValueMinutes(10L));
                scrollRequest.scroll(scroll);
                try {
                    searchResponse = restHighLevelClient.scroll(scrollRequest, RequestOptions.DEFAULT);
                } catch (Exception e) {
                    log.error("日志查询失败");
                }
            }
            //控制录制总流量数
            scanElements+=length;
            if(scanElements>=1000000){
                log.info("扫描流量超过100W，即将退出扫描");
                break;
            }
            SearchHit[] hits = searchResponse.getHits().getHits();
            log.info("查询到总流量 length" + hits.length + "");
            // 业务逻辑
            for (SearchHit at : searchResponse.getHits()) {
                String method = at.getSourceAsMap().get(methodKeyword).toString();
                String url = EsUtils.getUrlByRequest(at.getSourceAsMap().get(apiKeyWord).toString());
//                String timestamp = at.getSourceAsMap().get("@timestamp").toString();
//                log.info("timestamp = " + timestamp);
//                SimpleDateFormat inputFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
//                inputFormat.setTimeZone(TimeZone.getTimeZone("UTC")); // 设置时区为 UTC
//                Date date = inputFormat.parse(timestamp);
//
//                SimpleDateFormat outputFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
//                String formattedDate = outputFormat.format(date);
//                log.info("formattedDate = " + formattedDate);
                //根据方法名判断
                if ("OPTIONS".equals(method)||"HEAD".equals(method)) {
                    //再次过滤OPTIONS HEAD请求
                    continue;
                }
                //根据url判断
                if (EsUtils.shouldIgnore(url)||url==null||url.contains("null")||url.contains(".")) {
                    //再次过滤静态资源请求
                    continue;
                }
                log.info("url = " + url);
                url = EsUtils.truncateUrl(url);
                url = url.replaceAll("/$", "");
                if (url.isEmpty()) {
                    url = "/";
                }else {
                    url = url.replaceAll("[&?!].*","");
                }
                log.info("url === " + url);
                url = method + " "+ url;
                //url= url.replaceAll("null","{pp}");
                if(!apiMap.containsKey(url)){
                    apiMap.put(url,1);
                }else{
                    //控制单接口录制条数
                    if (apiMap.get(url) > 100) {
                        continue;
                    }
                    apiMap.put(url,apiMap.get(url)+1);
                }
            }
            // 批量插入接口流量数据到数据库中
            List<Api> apiList = new ArrayList<>();
            for (String s:apiMap.keySet()) {
                String str[] = s.split(" ");
                String method = str[0];
                String apiName = str[1];
//                String timestamp = str[3] + " " + str[4];
//                SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
//                Date date = format.parse(timestamp);
                int count = apiMap.get(s);
                Api newApi = new Api();
                newApi.setDepartmentId(100);
                newApi.setDomainId(domainId);
                newApi.setIsEnable(true);
                newApi.setIsOnline(true);
                newApi.setIsRead(true);
                newApi.setMethod(HttpMethodConstants.valueOf(method).getValue());
                newApi.setName(apiName);
                newApi.setRecordableCount(count);
                newApi.setCreateAt(LocalDateTime.now());
                newApi.setCreateBy(loginUser.getUser().getUserId().intValue());
                apiList.add(newApi);
            }
            if (!CollectionUtils.isEmpty(apiList)) {
                apiRepository.saveAll(apiList);
            }
            //清除scroll
            ClearScrollRequest clearScrollRequest = new ClearScrollRequest();
            clearScrollRequest.addScrollId(scrollId);//也可以选择setScrollIds()将多个scrollId一起使用
            ClearScrollResponse clearScrollResponse = null;
            try {
                clearScrollResponse = restHighLevelClient.clearScroll(clearScrollRequest, RequestOptions.DEFAULT);
            } catch (IOException e) {
                e.printStackTrace();
            }
            succeeded = clearScrollResponse.isSucceeded();
            log.info("ES succeeded = " + succeeded);
        }
        return succeeded ? 1 : 0;
    }

    @Override
    public Result<Object> recordResult(Integer apiId, Integer recordId) {
        List<RecordResult> byRecordIdAndApiId = recordResultRepository.findByRecordIdAndApiId(recordId, apiId);
        if (!CollectionUtils.isEmpty(byRecordIdAndApiId)) {
            for (RecordResult recordResult : byRecordIdAndApiId) {
                if (StringUtils.isBlank(recordResult.getBody()) || recordResult.getBody().equals("null")) {
                    recordResult.setBody("{}");
                }
                if (StringUtils.isBlank(recordResult.getHeader()) || recordResult.getHeader().equals("null")) {
                    recordResult.setHeader("{}");
                }
            }
        }
        return new Result<>(ResponseCode.SUCCESS, byRecordIdAndApiId);
    }

    @Override
    public Result<Object> saveRecordResult(RecordResult recordResult) {
        Optional<RecordResult> oldRecordResult = recordResultRepository.findById(recordResult.getId());
        if (!oldRecordResult.isPresent()) {
            throw new BaseException("未找到录制结果实体，record_result_id = " + recordResult.getId());
        }
        RecordResult recordResult1 = oldRecordResult.get();
        recordResult1.setBody(recordResult.getBody());
        recordResult1.setHeader(recordResult.getHeader());
        recordResultRepository.save(recordResult1);
        return new Result<>(ResponseCode.SUCCESS, recordResult.getId());
    }

    private void saveExcelApiList(Map<String,Integer> apiMap,String domain, HttpServletResponse response) throws Exception {
        deleteExcel(domain);
//        String path = ResourceUtils.getURL("classpath:").getPath() + "static/";//+domain+"域名接口.xlsx";
        String name = domain+".xlsx";
     /*  File file =new File(path);
       List<TestExcel> testExcelList =  readToList(2,3,file, TestExcel.class);
       System.out.println(testExcelList.toString());*/
        List shu = new ArrayList<Object>();
        List<String> title1 = new ArrayList<String>();
        title1.add("domainName");
        title1.add("apiName");
        title1.add("method");
        title1.add("isRead");
        title1.add("recordableCount");
        shu.add(title1);
        List<String> title = new ArrayList<String>();
        title.add("域名");
        title.add("接口名");
        title.add("请求方式（大写GET/POST）");
        title.add("读/写");
        title.add("可录制条数");
        shu.add(title);
        for (String s:apiMap.keySet()) {
            String str[] = s.split(" ");
            String method = str[0];
            String apiName = str[1];
            List heng2 = new ArrayList<String>();
            heng2.add(domain);
            heng2.add(apiName);
            heng2.add(method);
            heng2.add("");
            heng2.add(apiMap.get(s));
            shu.add(heng2);
        }
        File file = ExcelHanderUtil.excelWriterReturnFile(0, 1, filePath + name, shu, "Conan 流量回放平台接入接口模板");
        if(response == null) {
            return;
        }
        DownloadFileUtil. downloadFile(name,file,response);
        log.info("写入excel完成");
    }

    public void deleteExcel(String domain) throws FileNotFoundException {
        log.info("deleteFileByDomain");
        try {
            String path = ResourceUtils.getURL("classpath:").getPath() + "static/";//+domain+"域名接口.xlsx";
            String name = domain+"域名接口.xlsx";
            File file = new File(path + name);
            file.delete();
        } catch (Exception e) {
            log.error("删除文件" + e);
        }
    }


}
