package org.example.flash_sale;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class FlashSaleApplicationTests {

	@Autowired
	private RedisTemplate<String, Object> redisTemplate;

	@Test
	void contextLoads() {
	}

	@Test
	void testRedis() {
		// 使用Spring注入的RedisTemplate
		String key = "test:key";
		String value = "hello redis";

		// 写入
		redisTemplate.opsForValue().set(key, value);

		// 读取
		Object result = redisTemplate.opsForValue().get(key);
		System.out.println("Redis读取结果: " + result);

		// 验证
		assertNotNull(result);

		// // 清理
		// redisTemplate.delete(key);
		// assertNull(redisTemplate.opsForValue().get(key));

		System.out.println("Redis测试通过！");
	}
}
