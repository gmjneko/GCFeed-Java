package io.github.gmjneko.gcfeed.common.idempotency;

/**
 * 幂等业务方法向 HTTP 层暴露的最小结果契约。
 *
 * <p>业务结果可以同时携带正常响应数据；基础设施只关心是否是首次创建。
 */
public interface IdempotentResult {

    /**
     * @return {@code true} 表示首次成功创建，{@code false} 表示幂等重放
     */
    boolean created();
}
