/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.polyglot;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.StopIterationException;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.polyglot.PolyglotIteratorFactory.CacheFactory.StopNodeGen;
import com.oracle.truffle.polyglot.PolyglotIteratorFactory.CacheFactory.NextNodeGen;

import java.lang.reflect.Type;
import java.util.Iterator;

class PolyglotIterator<T> implements Iterator<T>, HostWrapper {

    private static final Object UNINITIALIZED = new Object();
    private static final Object STOP = new Object();

    final Object guestObject;
    final PolyglotLanguageContext languageContext;
    final Cache cache;
    private Object next;

    PolyglotIterator(Class<T> elementClass, Type elementType, Object array, PolyglotLanguageContext languageContext) {
        this.guestObject = array;
        this.languageContext = languageContext;
        this.cache = Cache.lookup(languageContext, array.getClass(), elementClass, elementType);
        this.next = UNINITIALIZED;
    }

    @Override
    public Object getGuestObject() {
        return guestObject;
    }

    @Override
    public PolyglotLanguageContext getLanguageContext() {
        return languageContext;
    }

    @Override
    public PolyglotContextImpl getContext() {
        return languageContext.context;
    }

    @Override
    public boolean hasNext() {
        if (next == UNINITIALIZED) {
            fetchNext();
        }
        return next != STOP;
    }

    @Override
    @SuppressWarnings("unchecked")
    public T next() {
        if (next == UNINITIALIZED) {
            fetchNext();
        }
        if (next == STOP) {
            throw (RuntimeException) cache.stop.call(languageContext, guestObject);
        } else {
            Object res = next;
            next = UNINITIALIZED;
            return (T) res;
        }
    }

    private void fetchNext() {
        next = cache.next.call(languageContext, guestObject);
    }

    @Override
    public String toString() {
        return HostWrapper.toString(this);
    }

    @Override
    public int hashCode() {
        return HostWrapper.hashCode(languageContext, guestObject);
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof PolyglotIterator) {
            return HostWrapper.equals(languageContext, guestObject, ((PolyglotIterator<?>) o).guestObject);
        } else {
            return false;
        }
    }

    static final class Cache {

        final Class<?> receiverClass;
        final Class<?> valueClass;
        final Type valueType;
        final CallTarget stop;
        final CallTarget next;
        final CallTarget apply;

        private Cache(Class<?> receiverClass, Class<?> valueClass, Type valueType) {
            this.receiverClass = receiverClass;
            this.valueClass = valueClass;
            this.valueType = valueType;
            this.stop = HostToGuestRootNode.createTarget(StopNodeGen.create(this));
            this.next = HostToGuestRootNode.createTarget(NextNodeGen.create(this));
            this.apply = HostToGuestRootNode.createTarget(new Apply(this));
        }

        static Cache lookup(PolyglotLanguageContext languageContext, Class<?> receiverClass, Class<?> valueClass, Type valueType) {
            Key cacheKey = new Key(receiverClass, valueClass, valueType);
            Cache cache = HostToGuestRootNode.lookupHostCodeCache(languageContext, cacheKey, Cache.class);
            if (cache == null) {
                cache = HostToGuestRootNode.installHostCodeCache(languageContext, cacheKey, new Cache(receiverClass, valueClass, valueType), Cache.class);
            }
            assert cache.receiverClass == receiverClass;
            assert cache.valueClass == valueClass;
            assert cache.valueType == valueType;
            return cache;
        }

        private static final class Key {

            private final Class<?> receiverClass;
            private final Class<?> valueClass;
            private final Type valueType;

            Key(Class<?> receiverClass, Class<?> valueClass, Type valueType) {
                assert receiverClass != null;
                assert valueClass != null;
                this.receiverClass = receiverClass;
                this.valueClass = valueClass;
                this.valueType = valueType;
            }

            @Override
            public int hashCode() {
                int res = receiverClass.hashCode();
                res = res * 31 + valueClass.hashCode();
                res = res * 31 + (valueType == null ? 0 : valueType.hashCode());
                return res;
            }

            @Override
            public boolean equals(Object obj) {
                if (this == obj) {
                    return true;
                } else if (obj == null || getClass() != obj.getClass()) {
                    return false;
                }
                Key other = (Key) obj;
                return valueType == other.valueType && valueClass == other.valueClass && receiverClass == other.receiverClass;
            }
        }

        abstract static class PolyglotIteratorNode extends HostToGuestRootNode {

            static final int LIMIT = 5;

            final Cache cache;

            PolyglotIteratorNode(Cache cache) {
                this.cache = cache;
            }

            @SuppressWarnings("unchecked")
            @Override
            protected Class<? extends TruffleObject> getReceiverType() {
                return (Class<? extends TruffleObject>) cache.receiverClass;
            }

            @Override
            public final String getName() {
                return "PolyglotIterator<" + cache.receiverClass + ", " + cache.valueType + ">." + getOperationName();
            }

            protected abstract String getOperationName();

        }

        abstract static class StopNode extends PolyglotIteratorNode {

            StopNode(Cache cache) {
                super(cache);
            }

            @Override
            protected String getOperationName() {
                return "stop";
            }

            @Specialization(limit = "LIMIT")
            @SuppressWarnings("unused")
            Object doCached(PolyglotLanguageContext languageContext, Object receiver, Object[] args,
                            @CachedLibrary("receiver") InteropLibrary iterators) {
                throw HostInteropErrors.stopIteration(languageContext, receiver, cache.valueType);
            }
        }

        abstract static class NextNode extends PolyglotIteratorNode {

            NextNode(Cache cache) {
                super(cache);
            }

            @Override
            protected String getOperationName() {
                return "next";
            }

            @Specialization(limit = "LIMIT")
            @SuppressWarnings("unused")
            Object doCached(PolyglotLanguageContext languageContext, Object receiver, Object[] args,
                            @CachedLibrary("receiver") InteropLibrary iterators,
                            @Cached ToHostNode toHost,
                            @Cached BranchProfile error) {
                try {
                    while (iterators.hasIteratorNextElement(receiver)) {
                        try {
                            return toHost.execute(iterators.getIteratorNextElement(receiver), cache.valueClass, cache.valueType, languageContext, true);
                        } catch (StopIterationException e) {
                            break;
                        } catch (UnsupportedMessageException e) {
                            continue;
                        }
                    }
                    return STOP;
                } catch (UnsupportedMessageException e) {
                    error.enter();
                    throw HostInteropErrors.iteratorUnsupported(languageContext, receiver, cache.valueType, "hasNext");
                }
            }
        }

        private static class Apply extends PolyglotIteratorNode {

            @Child private PolyglotExecuteNode apply = PolyglotExecuteNodeGen.create();

            Apply(Cache cache) {
                super(cache);
            }

            @Override
            protected String getOperationName() {
                return "apply";
            }

            @Override
            protected Object executeImpl(PolyglotLanguageContext languageContext, Object receiver, Object[] args) {
                return apply.execute(languageContext, receiver, args[ARGUMENT_OFFSET], Object.class, Object.class);
            }
        }
    }

    @CompilerDirectives.TruffleBoundary
    static <T> PolyglotIterator<T> create(PolyglotLanguageContext languageContext, Object iterable, boolean implementFunction, Class<T> elementClass, Type elementType) {
        if (implementFunction) {
            return new PolyglotIteratorAndFunction<>(elementClass, elementType, iterable, languageContext);
        } else {
            return new PolyglotIterator<>(elementClass, elementType, iterable, languageContext);
        }
    }
}
