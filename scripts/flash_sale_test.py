#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
秒杀系统并发压力测试脚本

功能：
1. 模拟多用户并发抢购（无需登录）
2. 可配置并发数、用户数、商品ID
3. 支持四种模式：
   - redis: Redis+Lua+Kafka（同步）
   - direct: 直接数据库（同步）
   - redis-async: Redis+Lua+Kafka（异步/虚拟线程）
   - direct-async: 直接数据库（异步/虚拟线程）
4. 统计成功/失败数量、响应时间、QPS

使用方法：
    # Redis + Lua + Kafka 方案（默认）
    python flash_sale_test.py --users 200 --concurrency 50 --product 1

    # 直接操作数据库方案
    python flash_sale_test.py --users 200 --concurrency 50 --product 1 --mode direct

    # 异步 Redis 方案（虚拟线程）
    python flash_sale_test.py --users 500 --concurrency 200 --product 1 --mode redis-async

    # 异步直接数据库方案（虚拟线程）
    python flash_sale_test.py --users 500 --concurrency 200 --product 1 --mode direct-async

依赖安装：
    pip install aiohttp
"""

import argparse
import asyncio
import aiohttp
import time
from dataclasses import dataclass
from typing import List
from collections import Counter
import statistics


@dataclass
class TestResult:
    """测试结果"""

    user_id: int
    success: bool
    message: str
    order_no: str
    response_time: float  # 毫秒
    status_code: int


class FlashSaleLoadTest:
    """秒杀压力测试类"""

    def __init__(self, base_url: str = "http://localhost:8080", mode: str = "redis"):
        self.base_url = base_url
        self.mode = mode  # "redis" 或 "direct"
        self.results: List[TestResult] = []

    @staticmethod
    def generate_fake_ip(user_id: int) -> str:
        """根据用户ID生成模拟IP地址"""
        b1 = 10
        b2 = (user_id // 65536) % 256
        b3 = (user_id // 256) % 256
        b4 = user_id % 256
        return f"{b1}.{b2}.{b3}.{b4}"

    def get_api_url(self) -> str:
        """根据模式返回不同的API地址"""
        urls = {
            "redis": f"{self.base_url}/api/flash/test/do",
            "direct": f"{self.base_url}/api/flash/test/do-direct",
            "redis-async": f"{self.base_url}/api/flash/test/do-async",
            "direct-async": f"{self.base_url}/api/flash/test/do-direct-async",
        }
        return urls.get(self.mode, urls["redis"])

    def get_mode_name(self) -> str:
        """获取模式的中文名称"""
        names = {
            "redis": "Redis + Lua + Kafka（同步）",
            "direct": "直接数据库（同步）",
            "redis-async": "Redis + Lua + Kafka（异步/虚拟线程）",
            "direct-async": "直接数据库（异步/虚拟线程）",
        }
        return names.get(self.mode, self.mode)

    async def do_flash_sale(
        self, session: aiohttp.ClientSession, user_id: int, product_id: int
    ) -> TestResult:
        """执行秒杀请求"""
        url = self.get_api_url()
        params = {"userId": user_id, "productId": product_id, "quantity": 1}

        # 模拟不同IP
        fake_ip = self.generate_fake_ip(user_id)
        headers = {
            "X-Forwarded-For": fake_ip,
            "X-Real-IP": fake_ip,
        }

        start_time = time.time()

        try:
            async with session.post(url, params=params, headers=headers) as response:
                response_time = (time.time() - start_time) * 1000
                status_code = response.status
                data = await response.json()

                success = data.get("code") == 200 and data.get("data", {}).get(
                    "success", False
                )
                message = (
                    data.get("data", {}).get("message", "")
                    if data.get("data")
                    else data.get("message", "")
                )
                order_no = data.get("data", {}).get("orderNo", "") if success else ""

                return TestResult(
                    user_id=user_id,
                    success=success,
                    message=message,
                    order_no=order_no,
                    response_time=response_time,
                    status_code=status_code,
                )
        except Exception as e:
            response_time = (time.time() - start_time) * 1000
            return TestResult(
                user_id=user_id,
                success=False,
                message=str(e),
                order_no="",
                response_time=response_time,
                status_code=0,
            )

    async def run_test(self, user_count: int, product_id: int, concurrency: int):
        """执行秒杀压测"""
        mode_name = self.get_mode_name()

        print(f"\n🚀 开始秒杀压测...")
        print(f"   测试模式: {mode_name}")
        print(f"   商品ID: {product_id}")
        print(f"   参与用户: {user_count}")
        print(f"   并发数: {concurrency}")
        print("-" * 50)

        connector = aiohttp.TCPConnector(limit=concurrency)
        timeout = aiohttp.ClientTimeout(total=120)

        async with aiohttp.ClientSession(
            connector=connector, timeout=timeout
        ) as session:
            semaphore = asyncio.Semaphore(concurrency)

            async def flash_sale_with_semaphore(user_id: int):
                async with semaphore:
                    return await self.do_flash_sale(session, user_id, product_id)

            start_time = time.time()

            tasks = [flash_sale_with_semaphore(i) for i in range(1, user_count + 1)]

            self.results = await asyncio.gather(*tasks)

            total_time = time.time() - start_time

        self.print_statistics(total_time)

    def print_statistics(self, total_time: float):
        """打印统计结果"""
        mode_name = self.get_mode_name()

        print("\n" + "=" * 60)
        print(f"📊 压测结果统计 [{mode_name}]")
        print("=" * 60)

        total = len(self.results)
        success_count = sum(1 for r in self.results if r.success)
        fail_count = total - success_count

        # 响应时间统计
        response_times = [r.response_time for r in self.results]
        avg_time = statistics.mean(response_times) if response_times else 0
        min_time = min(response_times) if response_times else 0
        max_time = max(response_times) if response_times else 0

        sorted_times = sorted(response_times)
        p50 = sorted_times[int(len(sorted_times) * 0.50)] if sorted_times else 0
        p95 = sorted_times[int(len(sorted_times) * 0.95)] if sorted_times else 0
        p99 = (
            sorted_times[min(int(len(sorted_times) * 0.99), len(sorted_times) - 1)]
            if sorted_times
            else 0
        )

        # 失败原因统计
        fail_reasons = Counter(r.message for r in self.results if not r.success)

        # QPS
        qps = total / total_time if total_time > 0 else 0

        print(f"\n📈 总体统计:")
        print(f"   总请求数: {total}")
        print(f"   成功数量: {success_count} ({success_count/total*100:.1f}%)")
        print(f"   失败数量: {fail_count} ({fail_count/total*100:.1f}%)")
        print(f"   总耗时: {total_time:.2f}秒")
        print(f"   QPS: {qps:.2f}")

        print(f"\n⏱️ 响应时间(ms):")
        print(f"   平均: {avg_time:.2f}")
        print(f"   最小: {min_time:.2f}")
        print(f"   最大: {max_time:.2f}")
        print(f"   P50: {p50:.2f}")
        print(f"   P95: {p95:.2f}")
        print(f"   P99: {p99:.2f}")

        if fail_reasons:
            print(f"\n❌ 失败原因分布:")
            for reason, count in fail_reasons.most_common(10):
                print(f"   {reason}: {count}次")

        if success_count > 0:
            success_results = [r for r in self.results if r.success]
            print(f"\n✅ 成功抢购用户: {len(success_results)}人")
            if len(success_results) <= 10:
                for r in success_results:
                    print(f"   用户{r.user_id}: {r.order_no}")
            else:
                for r in success_results[:5]:
                    print(f"   用户{r.user_id}: {r.order_no}")
                print(f"   ... 共{len(success_results)}人")

        print("\n" + "=" * 60)


async def main():
    parser = argparse.ArgumentParser(description="秒杀系统并发压力测试")
    parser.add_argument(
        "--url", type=str, default="http://localhost:8080", help="服务器地址"
    )
    parser.add_argument("--users", type=int, default=200, help="参与抢购的用户数量")
    parser.add_argument("--concurrency", type=int, default=50, help="并发数")
    parser.add_argument("--product", type=int, default=1, help="商品ID")
    parser.add_argument(
        "--mode",
        type=str,
        default="redis",
        choices=["redis", "direct", "redis-async", "direct-async"],
        help="测试模式: redis/direct(同步) 或 redis-async/direct-async(异步/虚拟线程)",
    )

    args = parser.parse_args()

    tester = FlashSaleLoadTest(args.url, args.mode)
    mode_name = tester.get_mode_name()

    print("=" * 60)
    print("⚡ 秒杀系统并发压力测试")
    print("=" * 60)
    print(f"服务器: {args.url}")
    print(f"测试模式: {mode_name}")
    print(f"用户数: {args.users}")
    print(f"并发数: {args.concurrency}")
    print(f"商品ID: {args.product}")

    await tester.run_test(args.users, args.product, args.concurrency)


if __name__ == "__main__":
    asyncio.run(main())
