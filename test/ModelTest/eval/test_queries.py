"""
测试查询集 — 模拟真实用户查询 + 期望命中的文档来源

每个查询标注:
- query: 用户查询文本
- source_file: 期望命中的知识库文档文件名
- relevant_section: 期望命中的文档段落关键词（用于验证 chunk 质量）
"""

from dataclasses import dataclass
from typing import Optional


@dataclass
class TestQuery:
    """测试查询"""
    query: str
    source_file: str
    relevant_section: str  # 期望命中的段落关键词
    category: str  # 查询类别（用于分组统计）


# ============ 测试查询集 ============
TEST_QUERIES = [
    # ---- 退票手续费 ----
    TestQuery(
        query="退票手续费怎么收的",
        source_file="refund-policy.txt",
        relevant_section="退票手续费标准",
        category="退票",
    ),
    TestQuery(
        query="开车前24小时退票收多少手续费",
        source_file="refund-policy.txt",
        relevant_section="不足24小时退票收取20%",
        category="退票",
    ),
    TestQuery(
        query="开车前8天以上退票要收钱吗",
        source_file="refund-policy.txt",
        relevant_section="8天以上退票不收手续费",
        category="退票",
    ),
    TestQuery(
        query="火车晚点了能全额退票吗",
        source_file="refund-policy.txt",
        relevant_section="列车晚点可全额退票",
        category="退票",
    ),
    TestQuery(
        query="退票时间怎么算的",
        source_file="refund-policy.txt",
        relevant_section="退票时间以列车出发站开车时间为准",
        category="退票",
    ),
    TestQuery(
        query="已经打印了报销凭证还能退票吗",
        source_file="refund-policy.txt",
        relevant_section="已打印报销凭证需交回",
        category="退票",
    ),

    # ---- 购票规则 ----
    TestQuery(
        query="儿童票怎么买",
        source_file="purchase-rules.txt",
        relevant_section="儿童票规定",
        category="购票",
    ),
    TestQuery(
        query="6岁小孩坐火车要买票吗",
        source_file="purchase-rules.txt",
        relevant_section="未满6周岁免费",
        category="购票",
    ),
    TestQuery(
        query="学生票什么时候可以买",
        source_file="purchase-rules.txt",
        relevant_section="学生票寒暑假期间",
        category="购票",
    ),
    TestQuery(
        query="学生票动车二等座打几折",
        source_file="purchase-rules.txt",
        relevant_section="动车组二等座75%",
        category="购票",
    ),
    TestQuery(
        query="一个人一天能买几张同车次的车票",
        source_file="purchase-rules.txt",
        relevant_section="同一身份证同一车次限购一张",
        category="购票",
    ),
    TestQuery(
        query="买火车票需要什么证件",
        source_file="purchase-rules.txt",
        relevant_section="有效身份证件",
        category="购票",
    ),
    TestQuery(
        query="可以在哪里买火车票",
        source_file="purchase-rules.txt",
        relevant_section="购票渠道",
        category="购票",
    ),

    # ---- FAQ ----
    TestQuery(
        query="支付失败了怎么办",
        source_file="faq.txt",
        relevant_section="支付失败检查网络余额",
        category="FAQ",
    ),
    TestQuery(
        query="付了钱但订单显示没付",
        source_file="faq.txt",
        relevant_section="支付状态延迟3-5分钟",
        category="FAQ",
    ),
    TestQuery(
        query="候补订单什么时候能成功",
        source_file="faq.txt",
        relevant_section="候补有余票时按顺序兑现",
        category="FAQ",
    ),
    TestQuery(
        query="候补兑现成功后怎么付款",
        source_file="faq.txt",
        relevant_section="候补自动扣款30分钟内支付",
        category="FAQ",
    ),
    TestQuery(
        query="候补可以取消吗",
        source_file="faq.txt",
        relevant_section="候补兑现前可取消",
        category="FAQ",
    ),
    TestQuery(
        query="什么情况下可以改签",
        source_file="faq.txt",
        relevant_section="开车前可改签一次",
        category="FAQ",
    ),
    TestQuery(
        query="改签后还能退票吗",
        source_file="faq.txt",
        relevant_section="改签后按新车次退票规则",
        category="FAQ",
    ),
    TestQuery(
        query="忘带身份证了怎么办",
        source_file="faq.txt",
        relevant_section="临时身份证明",
        category="FAQ",
    ),
    TestQuery(
        query="可以在中间站上车吗",
        source_file="faq.txt",
        relevant_section="中间站上车未乘区间不退",
        category="FAQ",
    ),
    TestQuery(
        query="错过火车了怎么办",
        source_file="faq.txt",
        relevant_section="开车后可改签当日其他列车",
        category="FAQ",
    ),
]


def get_queries_by_category(category: str) -> list[TestQuery]:
    """按类别筛选查询"""
    return [q for q in TEST_QUERIES if q.category == category]


def get_all_categories() -> list[str]:
    """获取所有查询类别"""
    return sorted(set(q.category for q in TEST_QUERIES))


def get_query_stats() -> dict[str, int]:
    """查询统计"""
    stats = {}
    for q in TEST_QUERIES:
        stats[q.category] = stats.get(q.category, 0) + 1
    return stats


if __name__ == "__main__":
    print(f"测试查询总数: {len(TEST_QUERIES)}")
    print(f"类别分布: {get_query_stats()}")
    print()
    for q in TEST_QUERIES:
        print(f"  [{q.category}] {q.query} → {q.source_file}")
