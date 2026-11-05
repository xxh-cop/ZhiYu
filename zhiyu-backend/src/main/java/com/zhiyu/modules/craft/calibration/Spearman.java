package com.zhiyu.modules.craft.calibration;

import java.util.Arrays;
import java.util.Comparator;

/**
 * Spearman 等级相关系数（阶段 1-1）。
 *
 * <p>选 Spearman 而非 Pearson：自评是 1~7 等级量表（序数数据），小样本、
 * 只假设单调不假设线性——等级相关是序数×比率口径的教科书选择。
 * 并列取平均秩；任一序列为常数（秩无方差）时返回 0（无相关证据，保守处理）。
 */
public final class Spearman {

    private Spearman() {
    }

    /**
     * 计算两列等长数据的 Spearman ρ。
     *
     * @param x 自评分数序列
     * @param y 行为指标序列（专注停留占比）
     * @return ρ ∈ [-1,1]；常数序列返回 0
     */
    public static double between(double[] x, double[] y) {
        if (x.length != y.length || x.length < 2) {
            throw new IllegalArgumentException("两序列必须等长且至少 2 个样本");
        }
        double[] rx = ranks(x);
        double[] ry = ranks(y);
        double mx = Arrays.stream(rx).average().orElse(0);
        double my = Arrays.stream(ry).average().orElse(0);
        double num = 0, dx = 0, dy = 0;
        for (int i = 0; i < rx.length; i++) {
            double ax = rx[i] - mx;
            double ay = ry[i] - my;
            num += ax * ay;
            dx += ax * ax;
            dy += ay * ay;
        }
        double denom = Math.sqrt(dx * dy);
        return denom == 0 ? 0 : num / denom;
    }

    /** 秩次（并列取平均秩），从 1 起。 */
    static double[] ranks(double[] values) {
        int n = values.length;
        Integer[] order = new Integer[n];
        for (int i = 0; i < n; i++) {
            order[i] = i;
        }
        Arrays.sort(order, Comparator.comparingDouble(i -> values[i]));
        double[] ranks = new double[n];
        int i = 0;
        while (i < n) {
            int j = i;
            while (j + 1 < n && Double.compare(values[order[j + 1]], values[order[i]]) == 0) {
                j++;
            }
            double avgRank = (i + j) / 2.0 + 1; // 秩从 1 起，并列段取平均
            for (int k = i; k <= j; k++) {
                ranks[order[k]] = avgRank;
            }
            i = j + 1;
        }
        return ranks;
    }
}
