/*
 * Copyright 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.car.arch.common;

import static java.util.Objects.requireNonNull;

import android.annotation.NonNull;
import android.annotation.Nullable;

import androidx.arch.core.util.Function;
import androidx.core.util.Pair;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.Transformations;

import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

/**
 * Utility methods for using {@link LiveData}. In general for Boolean operations, {@code null} is
 * treated as an "unknown" value, and operations may use short-circuit evaluation to determine the
 * result. LiveData may be in an uninitialized state where observers are not called when registered
 * (e.g. a {@link MutableLiveData} where {@link MutableLiveData#setValue(Object)} has not yet been
 * called). If a Boolean operation receives an uninitialized LiveData as either of its parameters,
 * the result will also be in an uninitialized state.
 */
@SuppressWarnings({"unused", "WeakerAccess"})
public class LiveDataFunctions {

    private LiveDataFunctions() {
    }

    private static volatile LiveData<?> sNullLiveData;
    private static volatile LiveData<Boolean> sTrueLiveData;
    private static volatile LiveData<Boolean> sFalseLiveData;

    /**
     * Returns a LiveData that always emits {@code null}. This is different than an uninitialized
     * LiveData in that observers will be called (with {@code null}) when registered.
     */
    public static <T> LiveData<T> nullLiveData() {
        if (sNullLiveData == null) {
            sNullLiveData = dataOf(null);
        }
        // null can fit any generic type
        // noinspection unchecked
        return (LiveData<T>) sNullLiveData;
    }

    /** Returns a LiveData that always emits {@code true}. */
    public static LiveData<Boolean> trueLiveData() {
        if (sTrueLiveData == null) {
            sTrueLiveData = dataOf(true);
        }
        return sTrueLiveData;
    }

    /** Returns a LiveData that always emits {@code false}. */
    public static LiveData<Boolean> falseLiveData() {
        if (sFalseLiveData == null) {
            sFalseLiveData = dataOf(false);
        }
        return sFalseLiveData;
    }

    /** Returns a LiveData that is initialized with {@code value}. */
    public static <T> MutableLiveData<T> dataOf(@Nullable T value) {
        MutableLiveData<T> data = new MutableLiveData<>();
        data.setValue(value);
        return data;
    }

    /**
     * Returns a LiveData that emits the opposite of {@code source} (or {@code null} if {@code
     * source} emits {@code null})
     */
    public static LiveData<Boolean> not(@NonNull LiveData<Boolean> source) {
        return Transformations.map(source, bool -> bool == null ? null : !bool);
    }

    /**
     * Returns a LiveData that emits {@code true} iff {@code source} emits {@code null}. Otherwise
     * emits {@code false}
     */
    public static LiveData<Boolean> emitsNull(@NonNull LiveData<?> source) {
        return Transformations.map(source, Objects::isNull);
    }

    /**
     * Returns a LiveData that emits the same value as {@code source}, but only notifies its
     * observers when the new value is distinct ({@link Objects#equals(Object, Object)}
     */
    public static <T> LiveData<T> distinct(@NonNull LiveData<T> source) {
        return distinct(source, Objects::equals);
    }

    /**
     * Returns a LiveData that emits the same value as {@code source}, but only notifies its
     * observers when the new value is distinct ({@code areEqual} returns {@code false})
     */
    public static <T> LiveData<T> distinct(@NonNull LiveData<T> source,
            @NonNull BiPredicate<T, T> areEqual) {
        return new MediatorLiveData<T>() {
            private boolean mInitialized = false;

            {
                addSource(source, value -> {
                    if (!mInitialized || !areEqual.test(value, getValue())) {
                        mInitialized = true;
                        setValue(value);
                    }
                });
            }
        };
    }

    /**
     * Create a LiveData that doesn't change when {@code isFrozen} emits {@code true}. If {@code
     * source} has updated while the data was frozen, it will be updated to the current value once
     * unfrozen.
     *
     * @param isFrozen the result will not update while this data emits {@code true}.
     * @param source   the source data for the result.
     * @return a LiveData that doesn't change when {@code isFrozen} emits {@code true}.
     */
    public static <T> LiveData<T> freezable(@NonNull LiveData<Boolean> isFrozen,
            @NonNull LiveData<T> source) {
        return new MediatorLiveData<T>() {

            private boolean mDirty = false;

            {
                addSource(requireNonNull(isFrozen), frozen -> {
                    if (frozen == Boolean.FALSE && mDirty) {
                        setValue(source.getValue());
                        mDirty = false;
                    }
                });
                addSource(requireNonNull(source), value -> {
                    if (isFrozen.getValue() != Boolean.FALSE) {
                        mDirty = true;
                    } else {
                        setValue(source.getValue());
                        mDirty = false;
                    }
                });
            }
        };
    }

    /**
     * Similar to {@link Transformations#map(LiveData, Function)}, but emits {@code null} when
     * {@code source} emits {@code null}. The input to {@code func} may be treated as not nullable.
     */
    public static <T, R> LiveData<R> mapNonNull(@NonNull LiveData<T> source,
            @NonNull Function<T, R> func) {
        return mapNonNull(source, null, func);
    }

    /**
     * Similar to {@link Transformations#map(LiveData, Function)}, but emits {@code nullValue} when
     * {@code source} emits {@code null}. The input to {@code func} may be treated as not nullable.
     */
    public static <T, R> LiveData<R> mapNonNull(@NonNull LiveData<T> source, @Nullable R nullValue,
            @NonNull Function<T, R> func) {
        return Transformations.map(source, value -> value == null ? nullValue : func.apply(value));
    }

    /**
     * Similar to {@link Transformations#switchMap(LiveData, Function)}, but emits {@code null} when
     * {@code source} emits {@code null}. The input to {@code func} may be treated as not nullable.
     */
    public static <T, R> LiveData<R> switchMapNonNull(@NonNull LiveData<T> source,
            @NonNull Function<T, LiveData<R>> func) {
        return switchMapNonNull(source, null, func);
    }

    /**
     * Similar to {@link Transformations#switchMap(LiveData, Function)}, but emits {@code nullValue}
     * when {@code source} emits {@code null}. The input to {@code func} may be treated as not
     * nullable.
     */
    public static <T, R> LiveData<R> switchMapNonNull(@NonNull LiveData<T> source,
            @Nullable R nullValue,
            @NonNull Function<T, LiveData<R>> func) {
        return Transformations.switchMap(source,
                value -> value == null ? nullLiveData() : func.apply(value));
    }

    /**
     * Similar to {@link Transformations#switchMap(LiveData, Function)}, but emits a FutureData,
     * which provides a loading field for operations which may take a long time to finish.
     *
     * This LiveData emits values only when the loading status of the output changes. It will never
     * emit {@code null}. If the output is loading, the emitted FutureData will have a null value
     * for the data.
     */
    public static <T, R> LiveData<FutureData<R>> loadingSwitchMap(LiveData<T> trigger,
            @NonNull Function<T, LiveData<R>> func) {
        LiveData<R> output = Transformations.switchMap(trigger, func);
        return new MediatorLiveData<FutureData<R>>() {
            {
                addSource(trigger, data -> setValue(new FutureData<>(true, null)));
                addSource(output, data ->
                        setValue(new FutureData<>(false, output.getValue())));
            }
        };
    }

    /**
     * Returns a LiveData that emits the logical AND of the two arguments. Also deals with {@code
     * null} and uninitalized values as follows:
     * <table>
     * <tr>
     * <th></th>
     * <th>T</th>
     * <th>F</th>
     * <th>N</th>
     * <th>U</th>
     * </tr>
     * <tr>
     * <th>T</th>
     * <td>T</td>
     * <td>F</td>
     * <td>N</td>
     * <td>U</td>
     * </tr>
     * <tr>
     * <th>F</th>
     * <td>F</td>
     * <td>F</td>
     * <td>F</td>
     * <td>U</td>
     * </tr>
     * <tr>
     * <th>N</th>
     * <td>N</td>
     * <td>F</td>
     * <td>N</td>
     * <td>U</td>
     * </tr>
     * <tr>
     * <th>U</th>
     * <td>U</td>
     * <td>U</td>
     * <td>U</td>
     * <td>U</td>
     * </tr>
     * </table>
     * <p>
     * T = {@code true}, F = {@code false}, N = {@code null}, U = uninitialized
     *
     * @return a LiveData that emits the logical AND of the two arguments
     */
    public static LiveData<Boolean> and(@NonNull LiveData<Boolean> x,
            @NonNull LiveData<Boolean> y) {
        return new BinaryOperation<>(
                x,
                y,
                (a, b) -> {
                    if (a == null) {
                        if (Boolean.FALSE.equals(b)) {
                            return false;
                        }
                        return null;
                    }
                    if (a) {
                        return b;
                    }
                    return false;
                });
    }

    /**
     * Returns a LiveData that emits the logical OR of the two arguments. Also deals with {@code
     * null} and uninitalized values as follows:
     * <table>
     * <tr>
     * <th></th>
     * <th>T</th>
     * <th>F</th>
     * <th>N</th>
     * <th>U</th>
     * </tr>
     * <tr>
     * <th>T</th>
     * <td>T</td>
     * <td>T</td>
     * <td>T</td>
     * <td>U</td>
     * </tr>
     * <tr>
     * <th>F</th>
     * <td>T</td>
     * <td>F</td>
     * <td>N</td>
     * <td>U</td>
     * </tr>
     * <tr>
     * <th>N</th>
     * <td>T</td>
     * <td>N</td>
     * <td>N</td>
     * <td>U</td>
     * </tr>
     * <tr>
     * <th>U</th>
     * <td>U</td>
     * <td>U</td>
     * <td>U</td>
     * <td>U</td>
     * </tr>
     * </table>
     * <p>
     * T = {@code true}, F = {@code false}, N = {@code null}, U = uninitialized
     *
     * @return a LiveData that emits the logical OR of the two arguments
     */
    public static LiveData<Boolean> or(@NonNull LiveData<Boolean> x, @NonNull LiveData<Boolean> y) {
        return new BinaryOperation<>(
                x,
                y,
                (a, b) -> {
                    if (a == null) {
                        if (Boolean.TRUE.equals(b)) {
                            return true;
                        }
                        return null;
                    }
                    if (!a) {
                        return b;
                    }
                    return true;
                });
    }

    /**
     * Returns a LiveData backed by {@code value} if and only if predicate emits {@code true}. Emits
     * {@code null} otherwise.
     * <p>
     * This is equivalent to {@code iff(predicate, Boolean::booleanValue, value)}
     *
     * @see #iff(LiveData, Predicate, LiveData)
     */
    public static <T> LiveData<T> iff(
            @NonNull LiveData<Boolean> predicate, @NonNull LiveData<T> value) {
        return iff(predicate, Boolean::booleanValue, value);
    }

    /**
     * Returns a LiveData backed by {@code value} if and only if the trigger emits a value that
     * causes {@code predicate} to return {@code true}. Emits {@code null} otherwise.
     */
    public static <P, T> LiveData<T> iff(
            @NonNull LiveData<P> trigger,
            @NonNull Predicate<? super P> predicate,
            @NonNull LiveData<T> value) {
        return new BinaryOperation<>(
                trigger, value, (p, v) -> p == null || !predicate.test(p) ? null : v);
    }

    /**
     * Returns a LiveData that is backed by {@code trueData} when the predicate emits {@code true},
     * {@code falseData} when the predicate emits {@code false}, and emits {@code null} when the
     * predicate emits {@code null}.
     * <p>
     * This is equivalent to {@code ifThenElse(predicate, Boolean::booleanValue, trueData,
     * falseData)}
     *
     * @param trueData  the LiveData whose value should be emitted when predicate is {@code true}
     * @param falseData the LiveData whose value should be emitted when predicate is {@code false}
     * @see #ifThenElse(LiveData, Predicate, LiveData, LiveData)
     */
    public static <T> LiveData<T> ifThenElse(
            @NonNull LiveData<Boolean> predicate,
            @NonNull LiveData<T> trueData,
            @NonNull LiveData<T> falseData) {
        return ifThenElse(predicate, Boolean::booleanValue, trueData, falseData);
    }

    /**
     * Returns a LiveData that is backed by {@code trueData} when the trigger satisfies the
     * predicate, {@code falseData} when the trigger does not satisfy the predicate, and emits
     * {@code null} when the trigger emits {@code null}.
     *
     * @param trueData  the LiveData whose value should be emitted when predicate returns {@code
     *                  true}
     * @param falseData the LiveData whose value should be emitted when predicate returns {@code
     *                  false}
     */
    public static <P, T> LiveData<T> ifThenElse(
            @NonNull LiveData<P> trigger,
            @NonNull Predicate<? super P> predicate,
            @NonNull LiveData<T> trueData,
            @NonNull LiveData<T> falseData) {
        return Transformations.switchMap(
                trigger,
                t -> {
                    if (t == null) {
                        return nullLiveData();
                    } else {
                        return predicate.test(t) ? trueData : falseData;
                    }
                });
    }

    /**
     * Returns a LiveData that emits {@code trueValue} when the predicate emits {@code true}, {@code
     * falseValue} when the predicate emits {@code false}, and emits {@code null} when the predicate
     * emits {@code null}.
     * <p>
     * This is equivalent to {@code ifThenElse(predicate, Boolean::booleanValue, trueValue,
     * falseValue)}
     *
     * @param trueValue  the value that should be emitted when predicate returns {@code true}
     * @param falseValue the value that should be emitted when predicate returns {@code false}
     * @see #ifThenElse(LiveData, Predicate, Object, Object)
     */
    public static <T> LiveData<T> ifThenElse(
            @NonNull LiveData<Boolean> predicate, @Nullable T trueValue, @Nullable T falseValue) {
        return ifThenElse(predicate, Boolean::booleanValue, trueValue, falseValue);
    }

    /**
     * Returns a LiveData that emits {@code trueValue} when the trigger satisfies the predicate,
     * {@code falseValue} when the trigger does not satisfy the predicate, and emits {@code null}
     * when the trigger emits {@code null}.
     *
     * @param trueValue  the value that should be emitted when predicate returns {@code true}
     * @param falseValue the value that should be emitted when predicate returns {@code false}
     */
    public static <P, T> LiveData<T> ifThenElse(
            @NonNull LiveData<P> trigger,
            @NonNull Predicate<? super P> predicate,
            @Nullable T trueValue,
            @Nullable T falseValue) {
        return Transformations.map(
                trigger,
                t -> {
                    if (t == null) {
                        return null;
                    }
                    return predicate.test(t) ? trueValue : falseValue;
                });
    }

    /**
     * Returns a LiveData that emits the value of {@code source} if it is not {@code null},
     * otherwise it emits the value of {@code fallback}.
     *
     * @param source   The LiveData whose value should be emitted if not {@code null}
     * @param fallback The LiveData whose value should be emitted when {@code source} emits {@code
     *                 null}
     */
    public static <T> LiveData<T> coalesceNull(@NonNull LiveData<T> source,
            @NonNull LiveData<T> fallback) {
        return new BinaryOperation<>(source, fallback, true, false,
                (sourceValue, fallbackValue) -> sourceValue == null ? fallbackValue : sourceValue);
    }

    /**
     * Returns a LiveData that emits the value of {@code source} if it is not {@code null},
     * otherwise it emits {@code fallback}.
     *
     * @param source   The LiveData whose value should be emitted if not {@code null}
     * @param fallback The value that should be emitted when {@code source} emits {@code null}
     */
    public static <T> LiveData<T> coalesceNull(@NonNull LiveData<T> source, T fallback) {
        return Transformations.map(source, value -> value == null ? fallback : value);
    }

    /**
     * Returns a LiveData that emits a Pair containing the values of the two parameter LiveDatas. If
     * either parameter is uninitialized, the resulting LiveData is also uninitialized.
     * <p>
     * This is equivalent to calling {@code combine(tData, uData, Pair::new)}.
     *
     * @see #combine(LiveData, LiveData, BiFunction)
     */
    public static <T, U> LiveData<Pair<T, U>> pair(
            @NonNull LiveData<T> tData, @NonNull LiveData<U> uData) {
        return combine(tData, uData, Pair::new);
    }

    /**
     * Returns an observer that splits a pair into two separate arguments. This method is mainly
     * used to simplify lambda expressions and enable method references, especially in combination
     * with {@link #pair(LiveData, LiveData)}.
     * <p>
     * Example:
     * <pre><code>
     * class MyViewModel extends ViewModel {
     *   LiveData&lt;Integer> getIntData() {...}
     *   LiveData&lt;Boolean> getBoolData() {...}
     * }
     *
     * void consume(int intValue, boolean booleanValue) {...}
     *
     * void startObserving(MyViewModel viewModel) {
     *   pair(viewModel.getIntData(), viewModel.getBoolData()).observe(owner, split(this::consume));
     * }</code></pre>
     */
    public static <T, U> Observer<Pair<T, U>> split(@NonNull BiConsumer<T, U> consumer) {
        return (pair) -> {
            if (pair == null) {
                consumer.accept(null, null);
            } else {
                consumer.accept(pair.first, pair.second);
            }
        };
    }

    /**
     * Returns a switch Function that splits a pair into two separate arguments. This method is
     * mainly used to simplify lambda expressions and enable method references for {@link
     * Transformations#switchMap(LiveData, Function) switchMaps}, especially in combination with
     * {@link #pair(LiveData, LiveData)}.
     */
    public static <T, U, V> Function<Pair<T, U>, LiveData<V>> split(
            @NonNull BiFunction<T, U, LiveData<V>> function) {
        return (pair) -> {
            if (pair == null) {
                return function.apply(null, null);
            } else {
                return function.apply(pair.first, pair.second);
            }
        };
    }

    /**
     * Returns a LiveData that emits the result of {@code function} on the values of the two
     * parameter LiveDatas. If either parameter is uninitialized, the resulting LiveData is also
     * uninitialized.
     */
    public static <T, U, R> LiveData<R> combine(
            @NonNull LiveData<T> tData,
            @NonNull LiveData<U> uData,
            @NonNull BiFunction<T, U, R> function) {
        return new BinaryOperation<>(tData, uData, function);
    }

    private static class BinaryOperation<T, U, R> extends MediatorLiveData<R> {
        @NonNull
        private final BiFunction<T, U, R> mFunction;

        private boolean mTSet;
        private boolean mUSet;
        private boolean mValueSet;

        @Nullable
        private T mTValue;
        @Nullable
        private U mUValue;

        BinaryOperation(
                @NonNull LiveData<T> tLiveData,
                @NonNull LiveData<U> uLiveData,
                @NonNull BiFunction<T, U, R> function) {
            this(tLiveData, uLiveData, true, true, function);
        }

        BinaryOperation(
                @NonNull LiveData<T> tLiveData,
                @NonNull LiveData<U> uLiveData,
                boolean requireTSet,
                boolean requireUSet,
                @NonNull BiFunction<T, U, R> function) {
            this.mFunction = function;
            if (!requireTSet) {
                mTSet = true;
            }
            if (!requireUSet) {
                mUSet = true;
            }
            if (tLiveData == uLiveData) {
                // Only add the source once and only update once when it changes.
                addSource(
                        tLiveData,
                        value -> {
                            mTSet = true;
                            mUSet = true;
                            mTValue = value;
                            // if both references point to the same LiveData, then T and U are
                            // compatible types.
                            // noinspection unchecked
                            mUValue = (U) value;
                            update();
                        });
            } else {
                addSource(requireNonNull(tLiveData), this::updateT);
                addSource(requireNonNull(uLiveData), this::updateU);
            }
        }

        private void updateT(@Nullable T tValue) {
            mTSet = true;
            this.mTValue = tValue;
            update();
        }

        private void updateU(@Nullable U uValue) {
            mUSet = true;
            this.mUValue = uValue;
            update();
        }

        private void update() {
            if (mTSet && mUSet) {
                R result = mFunction.apply(mTValue, mUValue);
                // Don't setValue if it's the same as the old value unless we haven't set a value
                // before.
                if (!mValueSet || result != getValue()) {
                    mValueSet = true;
                    setValue(result);
                }
            }
        }
    }
}
