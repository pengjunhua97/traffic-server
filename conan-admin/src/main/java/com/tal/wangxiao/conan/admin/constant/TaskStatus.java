package com.tal.wangxiao.conan.admin.constant;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 任务状态枚举类
 *
 * @author conan
 */
@Getter
@AllArgsConstructor
public enum TaskStatus {
    CONFIG("待配置", 0),
    RREDY("可录制", 10),
    RECORD("录制中", 11),
    RECORD_SUCCESS("录制成功", 12),
    RECORD_FAIL("录制失败", 13),
    REPLAY("回放中", 20),
    REPLAY_SUCCESS("回放成功", 21),
    REPLAY_FAIL("回放失败", 22),
    DIFF("比对中", 30),
    DIFF_SUCCESS("比对成功", 31),
    DIFF_FAIL("比对失败", 32),
    DOING("执行中", 100),
    END("执行完成", 101),
    WRONG("执行失败", 102);

    private String label;
    private Integer value;
}
