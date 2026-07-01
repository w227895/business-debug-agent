package com.fr.ai.debugagent.service;

import com.fr.ai.debugagent.domain.Scenario;
import org.springframework.stereotype.Service;

@Service
public class ScenarioIdentifier {

    public Scenario identify(String userMessage, String businessText) {
        String text = (userMessage + "\n" + businessText).toLowerCase();
        if (containsAny(text, "航变", "航班", "flight", "departure", "起飞", "新航班")) {
            return new Scenario("flight_change_mail", "航变邮件解析", 0.93, "将航变邮件解析成航变接口入参 JSON");
        }
        if (containsAny(text, "售后", "退款", "退票", "refund", "客服")) {
            return new Scenario("aftersale_mail", "售后邮件解析", 0.82, "将售后邮件解析成售后接口入参 JSON");
        }
        return new Scenario("flight_change_mail", "航变邮件解析", 0.55, "默认进入航变邮件解析调试，建议业务确认场景");
    }

    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword.toLowerCase())) {
                return true;
            }
        }
        return false;
    }
}
