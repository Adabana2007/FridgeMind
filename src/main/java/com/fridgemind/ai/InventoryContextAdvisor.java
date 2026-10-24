package com.fridgemind.ai;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 库存上下文注入 Advisor：把调用方放进请求 context 的库存片段并进系统提示词。
 * Advisor 自身无状态，片段按请求传入（与官方 advisor 通过 context 取参数的做法一致）。
 */
@Component
public class InventoryContextAdvisor implements BaseAdvisor {

    /** 请求 context 中库存上下文的键：调用方 put，本 Advisor 读取 */
    public static final String INVENTORY_CONTEXT_KEY = "fridgemind.inventoryContext";

    /** 顺序取负值：先于记忆/日志等后续 Advisor 改写系统提示词 */
    public static final int ORDER = -100;

    @Override
    public ChatClientRequest before(ChatClientRequest request, AdvisorChain chain) {
        Object context = request.context().get(INVENTORY_CONTEXT_KEY);
        if (!(context instanceof String inventoryContext) || !StringUtils.hasText(inventoryContext)) {
            return request;
        }

        Prompt prompt = request.prompt();
        List<Message> others = new ArrayList<>();
        String systemText = "";
        for (Message message : prompt.getInstructions()) {
            if (message.getMessageType() == MessageType.SYSTEM) {
                systemText = message.getText();
            } else {
                others.add(message);
            }
        }
        String merged = StringUtils.hasText(systemText)
                ? systemText + "\n\n" + inventoryContext
                : inventoryContext;

        // 合并后的系统提示词置于首位，其余消息保持原有顺序
        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(merged));
        messages.addAll(others);

        return request.mutate().prompt(prompt.mutate().messages(messages).build()).build();
    }

    @Override
    public ChatClientResponse after(ChatClientResponse response, AdvisorChain chain) {
        return response;
    }

    @Override
    public int getOrder() {
        return ORDER;
    }
}
