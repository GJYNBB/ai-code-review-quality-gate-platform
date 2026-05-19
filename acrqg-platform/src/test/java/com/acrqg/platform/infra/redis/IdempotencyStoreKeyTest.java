package com.acrqg.platform.infra.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link IdempotencyStore} 静态 key 构造器的纯字符串测试（无 Redis 依赖）。
 *
 * <p>完整的 Redis 行为测试（NX 语义、TTL 过期、SADD / EXPIRE 组合）将在 B0-A.14
 * 通过 Testcontainers 落地，本测试仅守住 key 命名约定不被无意修改。
 *
 * <p>Covers: R7.4 (webhook 幂等键), R8.4 (手动任务幂等键)。
 */
class IdempotencyStoreKeyTest {

    @Test
    @DisplayName("webhookKey 拼接 provider/repositoryId/eventId 三元组")
    void webhookKeyHappyPath() {
        String key = IdempotencyStore.webhookKey("github", "42", "evt_abc123");
        assertThat(key).isEqualTo("idem:webhook:github:42:evt_abc123");
    }

    @Test
    @DisplayName("webhookKey 拒绝任一空白参数，避免 key 坍缩")
    void webhookKeyRejectsBlankParts() {
        assertThatThrownBy(() -> IdempotencyStore.webhookKey(null, "42", "evt"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> IdempotencyStore.webhookKey("github", " ", "evt"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> IdempotencyStore.webhookKey("github", "42", ""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("taskKey 仅包装 idempotencyKey")
    void taskKeyHappyPath() {
        assertThat(IdempotencyStore.taskKey("abc-001")).isEqualTo("idem:task:abc-001");
    }

    @Test
    @DisplayName("taskKey 拒绝空白 idempotencyKey")
    void taskKeyRejectsBlank() {
        assertThatThrownBy(() -> IdempotencyStore.taskKey(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> IdempotencyStore.taskKey("   "))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
