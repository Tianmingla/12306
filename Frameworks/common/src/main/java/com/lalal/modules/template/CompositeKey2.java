package com.lalal.modules.template;

import java.util.Objects;

/**
 * 2个参数的通用组合键
 */
public class CompositeKey2<K1, K2> {
    private final K1 key1;
    private final K2 key2;

    public CompositeKey2(K1 key1, K2 key2) {
        this.key1 = key1;
        this.key2 = key2;
    }

    public K1 getKey1() { return key1; }
    public K2 getKey2() { return key2; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CompositeKey2<?, ?> that = (CompositeKey2<?, ?>) o;
        return Objects.equals(key1, that.key1) &&
                Objects.equals(key2, that.key2);
    }

    @Override
    public int hashCode() {
        return Objects.hash(key1, key2);
    }

    @Override
    public String toString() {
        return "Key2{" + "k1=" + key1 + ", k2=" + key2 + '}';
    }
}