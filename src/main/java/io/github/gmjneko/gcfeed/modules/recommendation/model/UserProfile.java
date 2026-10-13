package io.github.gmjneko.gcfeed.modules.recommendation.model;

/** 用户兴趣向量；空数组表示已计算过的冷启动画像。 */
public record UserProfile(double[] vector) {

    public static UserProfile empty() {
        return new UserProfile(new double[0]);
    }

}
