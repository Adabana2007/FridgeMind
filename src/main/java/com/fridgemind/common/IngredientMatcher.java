package com.fridgemind.common;

/** 库存食材与菜谱食材的匹配器：只按名称字面包含判定，不做同义词推断；AI 推荐与食谱服务共用 */
public final class IngredientMatcher {

    private IngredientMatcher() {
    }

    /** 库存食材与菜谱食材是否同一种：名称字面相等或互相包含（忽略大小写与首尾空格） */
    public static boolean match(String stockName, String recipeName) {
        // 不猜同义词：把"肉""蛋"这类泛化词归组会把不同食材判成同一种（鸡胸肉≈牛肉、鸡蛋≈皮蛋），
        // 菜谱被标成"食材齐备"、缺料清单跟着漏项；写法不统一应由录入侧出标准名解决
        String a = stockName.toLowerCase().trim();
        String b = recipeName.toLowerCase().trim();
        return a.equals(b) || a.contains(b) || b.contains(a);
    }
}
