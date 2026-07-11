#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
12306 压测结果分析脚本
功能：
  1. 解析 JMeter .jtl 结果文件
  2. 计算核心指标：RT (avg/p50/p90/p99/Max)、TPS、错误率
  3. 按接口分组统计
  4. 生成 Markdown 报告

用法：
  python analyze_results.py result.jtl
  python analyze_results.py result.jtl --output report.md
"""

import csv
import sys
import os
import argparse
from collections import defaultdict
from datetime import datetime

def parse_jtl(filepath: str) -> list:
    """解析 JMeter JTL (CSV) 文件"""
    samples = []
    with open(filepath, 'r', encoding='utf-8', errors='replace') as f:
        reader = csv.DictReader(f)
        for row in reader:
            try:
                samples.append({
                    'timestamp': int(row.get('timeStamp', 0)),
                    'elapsed': int(row.get('elapsed', 0)),
                    'label': row.get('label', ''),
                    'responseCode': row.get('responseCode', ''),
                    'success': row.get('success', 'true').lower() == 'true',
                    'threadName': row.get('threadName', ''),
                    'latency': int(row.get('Latency', 0)),
                })
            except (ValueError, TypeError):
                continue
    return samples


def percentile(sorted_list: list, p: float) -> int:
    """计算百分位数"""
    if not sorted_list:
        return 0
    idx = int(len(sorted_list) * p / 100)
    idx = min(idx, len(sorted_list) - 1)
    return sorted_list[idx]


def analyze_by_label(samples: list) -> dict:
    """按接口分组统计"""
    groups = defaultdict(list)
    for s in samples:
        groups[s['label']].append(s)

    results = {}
    for label, items in groups.items():
        rts = sorted([s['elapsed'] for s in items])
        latencies = sorted([s['latency'] for s in items])
        errors = sum(1 for s in items if not s['success'])
        total = len(items)

        # 计算时间跨度（秒）
        if len(items) > 1:
            time_span = (items[-1]['timestamp'] - items[0]['timestamp']) / 1000.0
        else:
            time_span = 1.0

        tps = total / time_span if time_span > 0 else 0

        results[label] = {
            'total': total,
            'errors': errors,
            'error_rate': errors / total * 100 if total > 0 else 0,
            'tps': tps,
            'rt_avg': sum(rts) / len(rts) if rts else 0,
            'rt_p50': percentile(rts, 50),
            'rt_p90': percentile(rts, 90),
            'rt_p95': percentile(rts, 95),
            'rt_p99': percentile(rts, 99),
            'rt_max': max(rts) if rts else 0,
            'rt_min': min(rts) if rts else 0,
            'latency_avg': sum(latencies) / len(latencies) if latencies else 0,
            'latency_p99': percentile(latencies, 99),
        }

    return results


def analyze_oversell(jtl_path: str) -> dict:
    """分析超卖测试结果"""
    samples = parse_jtl(jtl_path)
    purchase_samples = [s for s in samples if 'Purchase' in s['label'] or 'Rush' in s['label']]

    success_count = sum(1 for s in purchase_samples if s['success'])
    fail_count = sum(1 for s in purchase_samples if not s['success'])
    total = len(purchase_samples)

    return {
        'total_attempts': total,
        'successful_purchases': success_count,
        'failed_purchases': fail_count,
        'oversell': success_count > 1,  # 如果1张票，成功数应<=1
        'success_rate': success_count / total * 100 if total > 0 else 0,
    }


def generate_report(results: dict, jtl_path: str, oversell_result: dict = None) -> str:
    """生成 Markdown 报告"""
    lines = []
    lines.append(f"# 12306 压测结果报告")
    lines.append(f"")
    lines.append(f"- **测试时间**: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
    lines.append(f"- **数据文件**: `{os.path.basename(jtl_path)}`")
    lines.append(f"")

    # 全局汇总
    total_requests = sum(r['total'] for r in results.values())
    total_errors = sum(r['errors'] for r in results.values())
    avg_rt = sum(r['rt_avg'] * r['total'] for r in results.values()) / total_requests if total_requests > 0 else 0

    lines.append(f"## 全局汇总")
    lines.append(f"")
    lines.append(f"| 指标 | 值 |")
    lines.append(f"|------|------|")
    lines.append(f"| 总请求数 | {total_requests:,} |")
    lines.append(f"| 总错误数 | {total_errors:,} |")
    lines.append(f"| 错误率 | {total_errors/total_requests*100:.3f}% |" if total_requests > 0 else "| 错误率 | N/A |")
    lines.append(f"| 平均 RT | {avg_rt:.0f} ms |")
    lines.append(f"")

    # 按接口详细统计
    lines.append(f"## 接口级别统计")
    lines.append(f"")
    lines.append(f"| 接口 | 请求数 | 错误率 | TPS | 平均RT(ms) | P50(ms) | P90(ms) | P99(ms) | Max(ms) |")
    lines.append(f"|------|--------|--------|-----|-----------|---------|---------|---------|---------|")

    # 按请求数排序
    for label, r in sorted(results.items(), key=lambda x: -x[1]['total']):
        lines.append(
            f"| {label} | {r['total']:,} | {r['error_rate']:.2f}% | "
            f"{r['tps']:.1f} | {r['rt_avg']:.0f} | {r['rt_p50']} | "
            f"{r['rt_p90']} | {r['rt_p99']} | {r['rt_max']} |"
        )

    lines.append(f"")

    # 超卖验证
    if oversell_result:
        lines.append(f"## 超卖验证结果")
        lines.append(f"")
        lines.append(f"| 指标 | 值 |")
        lines.append(f"|------|------|")
        lines.append(f"| 抢票总尝试 | {oversell_result['total_attempts']} |")
        lines.append(f"| 成功购票数 | {oversell_result['successful_purchases']} |")
        lines.append(f"| 失败购票数 | {oversell_result['failed_purchases']} |")
        lines.append(f"| 是否超卖 | **{'❌ 超卖!' if oversell_result['oversell'] else '✅ 无超卖'}** |")
        lines.append(f"")

    # 简历亮点
    lines.append(f"## 简历可引用数据")
    lines.append(f"")
    lines.append(f"```")
    # 找关键接口的 P99
    search_rts = [r for l, r in results.items() if 'Search' in l or 'Query' in l]
    purchase_rts = [r for l, r in results.items() if 'Purchase' in l]

    search_p99 = max(r['rt_p99'] for r in search_rts) if search_rts else 0
    purchase_p99 = max(r['rt_p99'] for r in purchase_rts) if purchase_rts else 0
    search_avg = sum(r['rt_avg'] for r in search_rts) / len(search_rts) if search_rts else 0

    lines.append(f"全链路压测，2000测试账号，7:2:1混合场景")
    lines.append(f"查询接口: 平均RT {search_avg:.0f}ms, P99 {search_p99}ms")
    lines.append(f"购票接口: P99 {purchase_p99}ms")
    lines.append(f"整体错误率: {total_errors/total_requests*100:.3f}%" if total_requests > 0 else "整体错误率: N/A")
    if oversell_result and not oversell_result['oversell']:
        lines.append(f"超卖验证: 50并发抢1票，0超卖")
    lines.append(f"```")
    lines.append(f"")

    return '\n'.join(lines)


def main():
    parser = argparse.ArgumentParser(description='12306 压测结果分析')
    parser.add_argument('jtl', help='JMeter JTL 结果文件路径')
    parser.add_argument('--output', '-o', default=None, help='输出 Markdown 报告路径')
    parser.add_argument('--oversell', action='store_true', help='分析超卖测试结果')
    args = parser.parse_args()

    if not os.path.exists(args.jtl):
        print(f"❌ 文件不存在: {args.jtl}")
        sys.exit(1)

    samples = parse_jtl(args.jtl)
    print(f"解析 {len(samples)} 条请求记录")

    results = analyze_by_label(samples)

    oversell_result = None
    if args.oversell:
        oversell_result = analyze_oversell(args.jtl)

    report = generate_report(results, args.jtl, oversell_result)

    if args.output:
        with open(args.output, 'w', encoding='utf-8') as f:
            f.write(report)
        print(f"✅ 报告已写入: {args.output}")
    else:
        print(report)


if __name__ == '__main__':
    main()
