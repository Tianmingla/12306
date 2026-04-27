package com.lalal.modules.template;

import java.util.Objects;

/**
 * 3个参数的通用组合键
 */
public class CompositeKey3<K1, K2, K3> {
    private final K1 key1;
    private final K2 key2;
    private final K3 key3;

    public CompositeKey3(K1 key1, K2 key2, K3 key3) {
        this.key1 = key1;
        this.key2 = key2;
        this.key3 = key3;
    }

    public K1 getKey1() { return key1; }
    public K2 getKey2() { return key2; }
    public K3 getKey3() { return key3; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CompositeKey3<?, ?, ?> that = (CompositeKey3<?, ?, ?>) o;
        return Objects.equals(key1, that.key1) &&
                Objects.equals(key2, that.key2) &&
                Objects.equals(key3, that.key3);
    }

    @Override
    public int hashCode() {
        return Objects.hash(key1, key2, key3);
    }

    @Override
    public String toString() {
        return "Key3{" + "k1=" + key1 + ", k2=" + key2 + ", k3=" + key3 + '}';
    }
}