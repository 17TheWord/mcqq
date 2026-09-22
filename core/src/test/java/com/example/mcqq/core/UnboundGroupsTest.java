package com.example.mcqq.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * The registry behind {@code /qq bind}: what an operator is allowed to bind, and what they are told when the
 * answer is "nothing yet".
 *
 * <p>Worth testing without a server, because this is the only way to learn a group's openid without reading a
 * log — and if it gets that wrong, the operator has no way in.
 */
class UnboundGroupsTest {

    @Test
    void theFirstSightingIsReportedAndTheSecondIsNot() {
        UnboundGroups groups = new UnboundGroups();

        assertTrue(groups.remember("main", "AAAA"), "第一次要说话，日志才会出现那一行");
        assertFalse(groups.remember("main", "AAAA"), "同一个群再说一次不该再刷一行日志");
        assertEquals(1, groups.all().size(), "记住的是一个群，不是一条消息");
    }

    @Test
    void newestIsTheLastOneHeardFromEvenIfItWasSeenBefore() {
        UnboundGroups groups = new UnboundGroups();
        groups.remember("main", "AAAA");
        groups.remember("main", "BBBB");
        // AAAA 又说了一次 —— 它现在是"最近说话的那个"，裸 /qq bind 绑的应该是它。
        groups.remember("main", "AAAA");

        assertEquals("AAAA", groups.newest().groupOpenid());
    }

    @Test
    void aPrefixResolvesWhenItIsUnambiguousAndNotWhenItIsNot() {
        UnboundGroups groups = new UnboundGroups();
        groups.remember("main", "ABCDEF");
        groups.remember("main", "ABCXYZ");

        assertEquals("ABCDEF", groups.find("ABCD").groupOpenid(), "唯一前缀应该能找到");
        assertEquals("ABCDEF", groups.find("abcdef").groupOpenid(), "大小写不该影响");
        assertNull(groups.find("ABC"), "两个都匹配时宁可不绑，也不能绑错");
        assertNull(groups.find("ZZZ"), "找不到就是找不到");
    }

    @Test
    void whatIsBoundIsForgotten() {
        UnboundGroups groups = new UnboundGroups();
        groups.remember("main", "AAAA");
        groups.forget("AAAA");

        assertNull(groups.newest());
        assertTrue(groups.all().isEmpty());
    }

    @Test
    void theRegistryIsBounded() {
        UnboundGroups groups = new UnboundGroups();
        for (int i = 0; i < 40; i++) {
            groups.remember("main", "G" + i);
        }

        assertTrue(groups.all().size() <= 16, "没人绑的群不该让这个列表无限长：" + groups.all().size());
        assertEquals("G39", groups.newest().groupOpenid(), "最新的那个永远留着");
    }
}
