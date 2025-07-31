package com.tal.wangxiao.conan.sys.common.core.page;

import com.github.pagehelper.PageInfo;
import lombok.Data;

/**
 * @ClassName BasePageInfoResponse
 * @Description TODO
 * @Author 彭俊华
 * @Date 2025/7/30 18:08
 **/
@Data
public class BasePageInfoResponse<T> {

    /**
     * 总记录数
     */
    private long total;

    /**
     * 总页数
     */
    private long pages = 0;

    /**
     * 当前页
     */
    private long page = 0;

    /**
     * 当前页长度
     */
    private long size = 0;
    /**
     * 每页大小
     */
    private long pageSize = 0;

    /**
     * 结果数据
     */
    private T data = null;

    public static BasePageInfoResponse getPageResultByList(PageInfo pageInfo) {
        BasePageInfoResponse pageInfoHandlerUtils = new BasePageInfoResponse<>();
        pageInfoHandlerUtils.setTotal(pageInfo.getTotal());
        pageInfoHandlerUtils.setPages(pageInfo.getPages());
        pageInfoHandlerUtils.setPage(pageInfo.getPageNum());
        pageInfoHandlerUtils.setSize(pageInfo.getSize());
        pageInfoHandlerUtils.setPageSize(pageInfo.getPageSize());
        pageInfoHandlerUtils.setData(pageInfo.getList());
        return pageInfoHandlerUtils;
    }
}
