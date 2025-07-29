package com.tal.wangxiao.conan.utils.diff;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.tal.wangxiao.conan.utils.diff.core.TextDiffResult;
import com.tal.wangxiao.conan.utils.diff.diff_match_patch.Diff;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Resource;
import java.util.*;


/**
 * @author: dengkunnan
 * @date: 2021-01-15 14:55
 * @description: 文本比对工具
 * @blame Conan Team
 */

@Slf4j
public class TextDiffUtils {

    public final static String DIFF_OPERATOR_TYPE_DELETE = "DELETE";

    public final static String DIFF_OPERATOR_TYPE_INSERT = "INSERT";

    /**
     * 两个字符串对比
     *
     * @param baseString
     * @param compareString
     */
    public static TextDiffResult stringDiff(String baseString, String compareString) {
        TextDiffResult textDiffResult = new TextDiffResult();
        if (baseString == null && compareString == null) {
            textDiffResult.setTotalMsgCount(0);
            textDiffResult.setDiffMsgCount(0);
            textDiffResult.setEqual(true);
            return textDiffResult;
        }

        if (baseString == null || compareString == null) {
            textDiffResult.setTotalMsgCount(0);
            textDiffResult.setDiffMsgCount(0);
            textDiffResult.setEqual(false);
            return textDiffResult;
        }

        baseString = baseString.trim();
        compareString = compareString.trim();
        return diffMathHander(baseString, compareString, textDiffResult);
    }


    /**
     * 两个字符串对比,支持传入忽略字段
     *
     * @param baseString
     * @param compareString
     * @param ignoreFields
     */
    public static TextDiffResult stringDiff(String baseString, String compareString, Set<String> ignoreFields) {

        for (String ignoreField : ignoreFields) {
            try {
                baseString = baseString.replace(ignoreField, "");
                compareString = compareString.replace(ignoreField, "");
            } catch (Exception e) {
                log.error("忽略字段在对比的文本中不存在");
            }
        }
        return stringDiff(baseString, compareString);
    }


    private static TextDiffResult diffMathHander(String baseString, String compareString, TextDiffResult textDiffResult) {
        int totalMsgCount = baseString.length();
        if (baseString.equals(compareString)) {
            textDiffResult.setTotalMsgCount(totalMsgCount);
            textDiffResult.setDiffMsgCount(0);
            textDiffResult.setEqual(true);
            return textDiffResult;
        }
        diff_match_patch dmp = new diff_match_patch();
        List<diff_match_patch.Diff> diff_main = dmp.diff_main(baseString, compareString);
        //使用BaseString的长度作为totalMsgCount属性
        int diffMsgCount = 0;
        List<String> diffList = new ArrayList<String>();
        for (Diff stringDiff : diff_main) {
            if (DIFF_OPERATOR_TYPE_DELETE.equals(stringDiff.operation.name()) || DIFF_OPERATOR_TYPE_INSERT.equals(stringDiff.operation.name())) {
                diffList.add(stringDiff.text);
                diffMsgCount += stringDiff.text.length();
            }
        }
        List<Map<String, String>> maps = Lists.newArrayList();  //存放的结果集
        diff_main.forEach(diff -> {
            Map<String, String> map = Maps.newHashMap();
            if(diff_match_patch.Operation.EQUAL.equals(diff.operation)){  //无操作
                map.put("key",diff_match_patch.Operation.EQUAL.name());
                map.put("value",diff.text);
            }else if(diff_match_patch.Operation.DELETE.equals(diff.operation)){   //删除
                map.put("key",diff_match_patch.Operation.DELETE.name());
                map.put("value",diff.text);
            }else if(diff_match_patch.Operation.INSERT.equals(diff.operation)){   //插入
                map.put("key",diff_match_patch.Operation.INSERT.name());
                map.put("value",diff.text);
            }
            maps.add(map);
        });
        System.out.println("比较结果输出：" + maps);
        textDiffResult.setTotalMsgCount(totalMsgCount);
        textDiffResult.setDiffMsgCount(diffMsgCount);
        textDiffResult.setEqual(true);
        return textDiffResult;

    }

}
