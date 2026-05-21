package com.acrqg.platform.webhook.verifier;

import com.acrqg.platform.common.api.ErrorCode;
import com.acrqg.platform.common.exception.BusinessException;
import com.acrqg.platform.repository.domain.Provider;
import jakarta.annotation.PostConstruct;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 路由 {@link Provider} 到对应 {@link SignatureVerifier} 实现的工厂（B3-B.1）。
 *
 * <p>构造期注入 Spring 容器中所有 {@link SignatureVerifier} 实例；在
 * {@link PostConstruct} 阶段构建一份不可变的 {@link EnumMap}，避免每次请求
 * 重新查询 bean 容器。同 provider 出现多个 verifier 时抛
 * {@link IllegalStateException} 让应用启动失败——这是配置错误，应当尽早暴露。
 *
 * <p>查询接口 {@link #forProvider(Provider)} 在未注册 provider 时抛
 * {@code BusinessException(VALIDATION_ERROR, "unsupported provider")}；当上层
 * {@code WebhookController} 通过头部推断不出 provider 时也走该分支。
 *
 * <p>Covers: R7.1, R7.2, R23.3。
 */
@Component
public class SignatureVerifierFactory {

    private static final Logger log = LoggerFactory.getLogger(SignatureVerifierFactory.class);

    private final List<SignatureVerifier> verifiers;
    private Map<Provider, SignatureVerifier> byProvider;

    public SignatureVerifierFactory(List<SignatureVerifier> verifiers) {
        this.verifiers = verifiers;
    }

    @PostConstruct
    void init() {
        EnumMap<Provider, SignatureVerifier> map = new EnumMap<>(Provider.class);
        for (SignatureVerifier v : verifiers) {
            Provider p = v.provider();
            if (p == null) {
                throw new IllegalStateException(
                        "SignatureVerifier returned null provider: " + v.getClass().getName());
            }
            SignatureVerifier prev = map.putIfAbsent(p, v);
            if (prev != null) {
                throw new IllegalStateException(
                        "Duplicate SignatureVerifier for provider " + p + ": "
                                + prev.getClass().getName() + " vs " + v.getClass().getName());
            }
        }
        this.byProvider = Map.copyOf(map);
        if (log.isInfoEnabled()) {
            log.info("SignatureVerifierFactory initialized with {} verifier(s): {}",
                    byProvider.size(), byProvider.keySet());
        }
    }

    /**
     * 查询指定 provider 的签名校验器。
     *
     * @param provider 代码托管平台
     * @return 对应的 {@link SignatureVerifier}，永不为 {@code null}
     * @throws BusinessException 入参为 {@code null} 或未注册时抛
     *                           {@link ErrorCode#VALIDATION_ERROR}
     */
    public SignatureVerifier forProvider(Provider provider) {
        if (provider == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "unsupported provider");
        }
        SignatureVerifier v = byProvider.get(provider);
        if (v == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "unsupported provider");
        }
        return v;
    }
}
