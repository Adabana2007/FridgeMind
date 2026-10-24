package com.fridgemind.service;

import com.fridgemind.vo.IngredientDetailVO;
import com.fridgemind.vo.IngredientVO;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 食材服务
 */
public interface IngredientService {

    /** 手动添加食材 */
    IngredientDetailVO add(Long fridgeId, String name, Integer categoryId,
                           BigDecimal quantity, String unit, Long userId,
                           LocalDate expiryDate, LocalDate productionDate,
                           String remark);

    /** 批量添加食材（买菜一次录多件：公共字段统一 + 名称清单）；expiryDays 为统一保质期（今天起 N 天到期），null 表示不设置 */
    int batchAdd(Long fridgeId, List<String> names, Integer categoryId,
                 BigDecimal quantity, String unit, Long userId,
                 Integer expiryDays, String remark);

    List<IngredientVO> list(Long fridgeId, Integer categoryId, String keyword,
                            Integer status, Integer expiringWithinDays);

    IngredientDetailVO getById(Long ingredientId);

    /** 修正 AI 识别结果 */
    void correct(Long ingredientId, Long userId, String name, Integer categoryId, String remark);

    /** 单条消耗食材 */
    void consume(Long ingredientId, BigDecimal quantity, Integer confirmed, Long userId);

    void batchDelete(List<Long> ids, Long userId);

    /** 批量标记过期；confirmed 为 null 时按旧行为（日志不记处理方式），=3 时记为"已过期" */
    void batchExpire(List<Long> ids, Integer confirmed, Long userId);

    /** 批量消耗：confirmed 1=已食用 2=已丢弃（null 为不记处理方式），与单条消耗同一套口径 */
    void batchConsume(List<Long> ids, Integer confirmed, Long userId);

    void batchChangeCategory(List<Long> ids, Integer categoryId, Long userId);

    /** 远程取出指令：由 Service 在事务提交后下发（事务内只查设备状态，不做网络调用） */
    record RemoteRemoveCommand(String deviceSn, Long deviceItemId, int count) {
    }
}
