package com.tal.wangxiao.conan.common.utils;

import cn.hutool.poi.excel.ExcelUtil;
import cn.hutool.poi.excel.ExcelWriter;
import com.tal.wangxiao.conan.common.api.ResponseCode;
import com.tal.wangxiao.conan.common.model.Result;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.poi.ss.util.CellRangeAddress;

import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.net.URLEncoder;
import java.util.List;

/**
 * @author: dengkunnan
 * @date: 2020-09-09 17:49
 * @description:
 **/
@Slf4j
public class DownloadFileUtil {
    /**
     * 文件下载，支持File对象, 删除临时文件
     * @param fileName 文件名称，例如：profile.png
     * @param file 文件对象
     * @param response 响应对象
     * @throws Exception
     * @throws IOException
     */
    public static void downloadFile(String fileName, File file, HttpServletResponse response)
            throws Exception, IOException {
        if (StringUtils.isEmpty(fileName)) {
            log.error(">> 下载文件失败，文件名为空！");
            throw new Exception("文件名为空！");
        }

        // >>>>>>>>>>>>>>>>>>>> 开始下载 <<<<<<<<<<<<<<<<<<<<
        response.setContentType("application/octet-stream");
        // 下载文件能正常显示中文
        response.setHeader("Content-Disposition", "attachment;filename=" + URLEncoder.encode(fileName, "UTF-8"));

        byte[] buffer = new byte[1024];
        BufferedInputStream bis = null;
        InputStream is = null;
        OutputStream outputStream = null;
        try {
            is = new FileInputStream(file);
            bis = new BufferedInputStream(is);
            outputStream = response.getOutputStream();
            int i = bis.read(buffer);
            while (i != -1) {
                outputStream.write(buffer, 0, i);
                i = bis.read(buffer);
            }
        } catch (Exception e) {
            log.error(">> 文件下载失败，errMsg:{}", e.getMessage());
            e.printStackTrace();
        } finally {
            try {
                if (bis != null) bis.close();
                if (is != null) is.close();
                if (outputStream != null) outputStream.close();
            } catch (IOException e) {
                log.error(">> 流关闭失败，errMsg:{}", e.getMessage());
            }
        }
        log.info(">> 文件下载成功！fileName:{}", fileName);
    }

    private static boolean isMergedRegionExists(ExcelWriter writer, int firstRow, int lastRow, int firstCol, int lastCol) {
        CellRangeAddress newRegion = new CellRangeAddress(firstRow, lastRow, firstCol, lastCol);
        for (int i = 0; i < writer.getSheet().getNumMergedRegions(); i++) {
            CellRangeAddress existingRegion = writer.getSheet().getMergedRegion(i);
            if (existingRegion.equals(newRegion)) {
                return true;
            }
        }
        return false;
    }


    /**
     * 文件下载，支持File对象, 删除临时文件
     * @param fileName 文件名称，例如：profile.png
     * @param file 文件对象
     * @param response 响应对象
     * @throws Exception
     * @throws IOException
     */
    public static void downloadFileNoDelete(String fileName, File file, HttpServletResponse response)
            throws Exception, IOException {
        if (StringUtils.isEmpty(fileName)) {
            log.error(">> 下载文件失败，文件名为空！");
            throw new Exception("文件名为空！");
        }

        // >>>>>>>>>>>>>>>>>>>> 开始下载 <<<<<<<<<<<<<<<<<<<<
        response.setContentType("application/octet-stream");
        // 下载文件能正常显示中文
        response.setHeader("Content-Disposition", "attachment;filename=" + URLEncoder.encode(fileName, "UTF-8"));

        byte[] buffer = new byte[1024];
        BufferedInputStream bis = null;
        InputStream is = null;
        OutputStream outputStream = null;
        try {
            is = new FileInputStream(file);
            bis = new BufferedInputStream(is);
            outputStream = response.getOutputStream();
            int i = bis.read(buffer);
            while (i != -1) {
                outputStream.write(buffer, 0, i);
                i = bis.read(buffer);
            }
        } catch (Exception e) {
            log.error(">> 文件下载失败，errMsg:{}", e.getMessage());
            e.printStackTrace();
        } finally {
            if (bis != null) {
                try {
                    if (outputStream != null) {
                        outputStream.close();
                    }
                    is.close();
                    bis.close();

                } catch (IOException e) {
                    log.error(">> 文件下载失败，errMsg:{}", e.getMessage());
                    e.printStackTrace();
                }
            }
        }
        log.info(">> 文件下载成功！fileName:{}", fileName);
    }
}
