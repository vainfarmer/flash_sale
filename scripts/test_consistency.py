#!/usr/bin/env python3
"""
库存一致性检查测试脚本
用于测试和验证Redis与数据库之间的库存一致性检查和修复功能
"""

import argparse
import json
import requests
from typing import Optional


class ConsistencyTester:
    def __init__(self, base_url: str = "http://localhost:8080"):
        self.base_url = base_url

    def check_single(self, product_id: int) -> dict:
        """检查单个商品的一致性"""
        url = f"{self.base_url}/api/admin/consistency/check/{product_id}"
        response = requests.get(url)
        return response.json()

    def check_all(self) -> dict:
        """检查所有商品的一致性"""
        url = f"{self.base_url}/api/admin/consistency/check-all"
        response = requests.get(url)
        return response.json()

    def repair_single(self, product_id: int, use_db: bool = True) -> dict:
        """修复单个商品的一致性"""
        url = f"{self.base_url}/api/admin/consistency/repair/{product_id}"
        response = requests.post(url, params={"useDbAsSource": str(use_db).lower()})
        return response.json()

    def repair_all(self) -> dict:
        """修复所有商品的一致性"""
        url = f"{self.base_url}/api/admin/consistency/repair-all"
        response = requests.post(url)
        return response.json()

    def get_report(self) -> dict:
        """获取一致性报告"""
        url = f"{self.base_url}/api/admin/consistency/report"
        response = requests.get(url)
        return response.json()

    def print_report(self, report: dict):
        """打印格式化的报告"""
        if report.get("code") != 200:
            print(f"❌ 请求失败: {report.get('message')}")
            return

        data = report.get("data", [])
        print("\n" + "=" * 80)
        print("📊 库存一致性检查报告")
        print("=" * 80)
        
        consistent_count = 0
        inconsistent_count = 0

        for item in data:
            status = "✅" if item["isConsistent"] else "❌"
            print(f"\n{status} 商品ID: {item['productId']} - {item['productName']}")
            print(f"   DB库存: {item['dbStock']}, Redis库存: {item['redisStock']}, 差异: {item.get('stockDiff', 'N/A')}")
            print(f"   DB已售: {item['dbSoldCount']}, Redis已售: {item['redisSoldCount']}, 差异: {item.get('soldDiff', 'N/A')}")
            print(f"   状态: {item['message']}")
            
            if item["isConsistent"]:
                consistent_count += 1
            else:
                inconsistent_count += 1

        print("\n" + "-" * 80)
        print(f"📈 统计: 一致={consistent_count}, 不一致={inconsistent_count}, 总计={len(data)}")
        print("=" * 80)


def main():
    parser = argparse.ArgumentParser(description="库存一致性检查工具")
    parser.add_argument(
        "--url", type=str, default="http://localhost:8080", help="后端服务地址"
    )
    parser.add_argument(
        "--action",
        type=str,
        choices=["check", "check-all", "repair", "repair-all", "report"],
        default="report",
        help="操作类型",
    )
    parser.add_argument("--product-id", type=int, help="商品ID（check和repair时需要）")
    parser.add_argument(
        "--use-redis",
        action="store_true",
        help="修复时以Redis为准（默认以数据库为准）",
    )

    args = parser.parse_args()

    tester = ConsistencyTester(args.url)

    try:
        if args.action == "check":
            if not args.product_id:
                print("❌ 请指定 --product-id")
                return
            result = tester.check_single(args.product_id)
            print(json.dumps(result, indent=2, ensure_ascii=False))

        elif args.action == "check-all":
            result = tester.check_all()
            print(json.dumps(result, indent=2, ensure_ascii=False))

        elif args.action == "repair":
            if not args.product_id:
                print("❌ 请指定 --product-id")
                return
            result = tester.repair_single(args.product_id, not args.use_redis)
            print(json.dumps(result, indent=2, ensure_ascii=False))

        elif args.action == "repair-all":
            print("🔧 开始修复所有不一致的商品...")
            result = tester.repair_all()
            print(json.dumps(result, indent=2, ensure_ascii=False))

        elif args.action == "report":
            result = tester.get_report()
            tester.print_report(result)

    except requests.exceptions.ConnectionError:
        print(f"❌ 无法连接到服务器 {args.url}")
    except Exception as e:
        print(f"❌ 发生错误: {e}")


if __name__ == "__main__":
    main()

