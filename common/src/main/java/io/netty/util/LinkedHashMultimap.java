/*
 * Copyright 2014 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package io.netty.util;

import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NoSuchElementException;
import java.util.Set;

public class LinkedHashMultimap<K, V> implements Multimap<K, V> {

    private static final int BUCKET_SIZE = 17;

    private static final Comparator<Object> DEFAULT_COMPARATOR = new Comparator<Object>() {
        @Override
        @SuppressWarnings("unchecked")
        public int compare(Object o1, Object o2) {
            return ((Comparable<Object>) o1).compareTo(o2);
        }
    };

    private static int index(int hash) {
        return hash % BUCKET_SIZE;
    }

    @SuppressWarnings("unchecked")
    private final HeaderEntry<K, V>[] entries = new HeaderEntry[BUCKET_SIZE];
    private final HeaderEntry<K, V> head = new HeaderEntry<K, V>(this);
    private final Comparator<? super K> keyComparator;
    int size;

    public LinkedHashMultimap() {
        this(null);
    }

    public LinkedHashMultimap(Comparator<? super K> keyComparator) {
        if (keyComparator == null) {
            keyComparator = DEFAULT_COMPARATOR;
        }
        head.before = head.after = head;
        this.keyComparator = keyComparator;
    }

    protected int hashKey(K key) {
        return key.hashCode();
    }

    protected void validateKey(K key) {
        if (key == null) {
            throw new NullPointerException("key");
        }
    }

    @SuppressWarnings("unchecked")
    protected V convertValue(Object value) {
        if (value == null) {
            throw new NullPointerException("value");
        }
        return (V) value;
    }

    @Override
    public Multimap<K, V> add(K key, Object value) {
        validateKey(key);
        V convertedVal = convertValue(value);
        int h = hashKey(key);
        int i = index(h);
        add0(h, i, key, convertedVal);
        return this;
    }

    @Override
    public Multimap<K, V> add(K key, Iterable<?> values) {
        validateKey(key);
        if (values == null) {
            throw new NullPointerException("values");
        }

        int h = hashKey(key);
        int i = index(h);
        for (Object v: values) {
            if (v == null) {
                break;
            }
            V convertedVal = convertValue(v);
            add0(h, i, key, convertedVal);
        }
        return this;
    }

    @Override
    public Multimap<K, V> add(K key, Object... values) {
        validateKey(key);
        if (values == null) {
            throw new NullPointerException("values");
        }

        int h = hashKey(key);
        int i = index(h);
        for (Object v: values) {
            if (v == null) {
                break;
            }
            V convertedVal = convertValue(v);
            add0(h, i, key, convertedVal);
        }
        return this;
    }

    private void add0(int h, int i, K key, V value) {
        // Update the hash table.
        HeaderEntry<K, V> e = entries[i];
        HeaderEntry<K, V> newEntry;
        entries[i] = newEntry = new HeaderEntry<K, V>(this, h, key, value);
        newEntry.next = e;

        // Update the linked list.
        newEntry.addBefore(head);
    }

    @Override
    public Multimap<K, V> add(Multimap<? extends K, ?> multimap) {
        if (multimap == null) {
            throw new NullPointerException("multimap");
        }

        add0(multimap);
        return this;
    }

    private void add0(Multimap<? extends K, ?> multimap) {
        if (multimap.isEmpty()) {
            return;
        }

        if (multimap instanceof LinkedHashMultimap) {
            @SuppressWarnings("unchecked")
            LinkedHashMultimap<K, ?> m = (LinkedHashMultimap<K, ?>) multimap;
            HeaderEntry<K, ?> e = m.head.after;
            while (e != m.head) {
                K key = e.key;
                validateKey(key);
                add(key, convertValue(e.value));
                e = e.after;
            }
        } else {
            for (Entry<? extends K, ?> e: multimap) {
                add(e.getKey(), e.getValue());
            }
        }
    }

    @Override
    public boolean remove(K key) {
        if (key == null) {
            throw new NullPointerException("key");
        }
        int h = hashKey(key);
        int i = index(h);
        return remove0(h, i, key);
    }

    private boolean remove0(int h, int i, K key) {
        HeaderEntry<K, V> e = entries[i];
        if (e == null) {
            return false;
        }

        boolean removed = false;
        for (;;) {
            if (e.hash == h && keyComparator.compare(key, e.key) == 0) {
                e.remove();
                HeaderEntry<K, V> next = e.next;
                if (next != null) {
                    entries[i] = next;
                    e = next;
                } else {
                    entries[i] = null;
                    return true;
                }
                removed = true;
            } else {
                break;
            }
        }

        for (;;) {
            HeaderEntry<K, V> next = e.next;
            if (next == null) {
                break;
            }
            if (next.hash == h && keyComparator.compare(key, next.key) == 0) {
                e.next = next.next;
                next.remove();
                removed = true;
            } else {
                e = next;
            }
        }

        return removed;
    }

    @Override
    public Multimap<K, V> set(K key, Object value) {
        validateKey(key);
        V convertedVal = convertValue(value);
        int h = hashKey(key);
        int i = index(h);
        remove0(h, i, key);
        add0(h, i, key, convertedVal);
        return this;
    }

    @Override
    public Multimap<K, V> set(K key, Iterable<?> values) {
        validateKey(key);
        if (values == null) {
            throw new NullPointerException("values");
        }

        int h = hashKey(key);
        int i = index(h);

        remove0(h, i, key);
        for (Object v: values) {
            if (v == null) {
                break;
            }
            V convertedVal = convertValue(v);
            add0(h, i, key, convertedVal);
        }

        return this;
    }

    @Override
    public Multimap<K, V> set(K key, Object... values) {
        validateKey(key);
        if (values == null) {
            throw new NullPointerException("values");
        }

        int h = hashKey(key);
        int i = index(h);

        remove0(h, i, key);
        for (Object v: values) {
            if (v == null) {
                break;
            }
            V convertedVal = convertValue(v);
            add0(h, i, key, convertedVal);
        }

        return this;
    }

    @Override
    public Multimap<K, V> set(Multimap<? extends K, ?> multimap) {
        if (multimap == null) {
            throw new NullPointerException("multimap");
        }

        clear();
        add0(multimap);
        return this;
    }

    @Override
    public Multimap<K, V> clear() {
        Arrays.fill(entries, null);
        head.before = head.after = head;
        size = 0;
        return this;
    }

    @Override
    public V get(K key) {
        if (key == null) {
            throw new NullPointerException("key");
        }

        int h = hashKey(key);
        int i = index(h);
        HeaderEntry<K, V> e = entries[i];
        V value = null;
        // loop until the first header was found
        while (e != null) {
            if (e.hash == h && keyComparator.compare(key, e.key) == 0) {
                value = e.value;
            }

            e = e.next;
        }
        if (value != null) {
            return value;
        }
        return null;
    }

    @Override
    public Collection<V> getAll(K key) {
        if (key == null) {
            throw new NullPointerException("key");
        }

        LinkedList<V> values = new LinkedList<V>();

        int h = hashKey(key);
        int i = index(h);
        HeaderEntry<K, V> e = entries[i];
        while (e != null) {
            if (e.hash == h && keyComparator.compare(key, e.key) == 0) {
                values.addFirst(e.getValue());
            }
            e = e.next;
        }
        return values;
    }

    @Override
    public List<Map.Entry<K, V>> entries() {
        List<Map.Entry<K, V>> all = new LinkedList<Map.Entry<K, V>>();
        HeaderEntry<K, V> e = head.after;
        while (e != head) {
            all.add(e);
            e = e.after;
        }
        return all;
    }

    @Override
    public Iterator<Entry<K, V>> iterator() {
        return new HeaderIterator();
    }

    @Override
    public boolean contains(K key) {
        return get(key) != null;
    }

    @Override
    public boolean isEmpty() {
        return head == head.after;
    }

    @Override
    public boolean contains(K name, Object value) {
        return contains(name, value, DEFAULT_COMPARATOR);
    }

    @Override
    public boolean contains(K name, Object value, Comparator<? super V> valueComparator) {
        if (name == null) {
            throw new NullPointerException("name");
        }

        int h = hashKey(name);
        int i = index(h);
        V convertedVal = convertValue(value);
        HeaderEntry<K, V> e = entries[i];
        while (e != null) {
            if (e.hash == h && keyComparator.compare(name, e.key) == 0) {
                if (valueComparator.compare(e.value, convertedVal) == 0) {
                    return true;
                }
            }
            e = e.next;
        }
        return false;
    }

    @Override
    public Set<K> keys() {
        Set<K> keys = new LinkedHashSet<K>();
        HeaderEntry<K, V> e = head.after;
        while (e != head) {
            keys.add(e.getKey());
            e = e.after;
        }
        return keys;
    }

    private final class HeaderIterator implements Iterator<Map.Entry<K, V>> {

        private HeaderEntry<K, V> current = head;

        @Override
        public boolean hasNext() {
            return current.after != head;
        }

        @Override
        public Entry<K, V> next() {
            current = current.after;

            if (current == head) {
                throw new NoSuchElementException();
            }

            return current;
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }
    }

    private static final class HeaderEntry<K, V> implements Map.Entry<K, V> {
        private final LinkedHashMultimap<K, V> parent;
        final int hash;
        final K key;
        V value;
        HeaderEntry<K, V> next;
        HeaderEntry<K, V> before, after;

        HeaderEntry(LinkedHashMultimap<K, V> parent, int hash, K key, V value) {
            this.parent = parent;
            this.hash = hash;
            this.key = key;
            this.value = value;
        }

        HeaderEntry(LinkedHashMultimap<K, V> parent) {
            this.parent = parent;
            hash = -1;
            key = null;
            value = null;
        }

        void remove() {
            before.after = after;
            after.before = before;
            parent.size --;
        }

        void addBefore(HeaderEntry<K, V> e) {
            after  = e;
            before = e.before;
            before.after = this;
            after.before = this;
            parent.size ++;
        }

        @Override
        public K getKey() {
            return key;
        }

        @Override
        public V getValue() {
            return value;
        }

        @Override
        public V setValue(V value) {
            if (value == null) {
                throw new NullPointerException("value");
            }
            value = parent.convertValue(value);
            V oldValue = this.value;
            this.value = value;
            return oldValue;
        }

        @Override
        public String toString() {
            return key.toString() + '=' + value.toString();
        }
    }
}
