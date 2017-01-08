package io.grpc.monitoring.streamz;

import com.google.common.base.Function;
import com.google.common.base.Objects;

import javax.annotation.Nullable;
import java.io.Serializable;
import java.util.Comparator;

/**
 * An immutable, semantic-free ordered pair of nullable values. These can be
 * accessed using the {@link #getFirst()} and {@link #getSecond()} methods. Equality
 * and hashing are defined in the natural way.
 *
 * <p>This type is devoid of semantics, best used for simple mechanical
 * aggregations of unrelated values in implementation code. Avoid using it in
 * your APIs, preferring an explicit type that conveys the exact semantics of
 * the data. For example, instead of: <pre>   {@code
 *
 *   Pair<T, T> findMinAndMax(List<T> list) {...}}</pre>
 *
 * ... use: <pre>   {@code
 *
 *   Range<T> findRange(List<T> list) {...}}</pre>
 *
 * <p>This usually involves creating a new custom value-object type. This is
 * difficult to do "by hand" in Java, but avoid the temptation to extend {@code
 * Pair} to accomplish this.
 *
 * TODO(avrukin) clean up and get rid of
 *
 * @author kevinb@google.com (Kevin Bourrillion)
 */
public class Pair<A, B> implements Serializable {
    /**
     * Creates a new pair containing the given elements in order.
     */
    public static <A, B> Pair<A, B> of(@Nullable A first, @Nullable B second) {
        return new Pair<A, B>(first, second);
    }

    /**
     * The first element of the pair.
     */
    public final A first;

    /**
     * The second element of the pair.
     */
    public final B second;

    /**
     * For subclass usage only. To create a new pair, use {@code Pair.of(first, second)}.
     */
    protected Pair(@Nullable A first, @Nullable B second) {
        this.first = first;
        this.second = second;
    }

    /**
     * Returns the first element of this pair.
     */
    public A getFirst() {
        return first;
    }

    /**
     * Returns the second element of this pair.
     */
    public B getSecond() {
        return second;
    }

    /** Returns a function that yields {@link #first}. */
    @SuppressWarnings("unchecked") // implementation is "fully variant"
    public static <A, B> Function<Pair<A, B>, A> firstFunction() {
        return (Function) PairFirstFunction.INSTANCE;
    }

    /** Returns a function that yields {@link #second}. */
    @SuppressWarnings("unchecked") // implementation is "fully variant"
    public static <A, B> Function<Pair<A, B>, B> secondFunction() {
        return (Function) PairSecondFunction.INSTANCE;
    }

    /*
     * Note that this implementation doesn't involve B, and A's are only "passed
     * through", so it has become "fully variant" in both parameters.
     */
    private static final class PairFirstFunction<A, B>
            implements Function<Pair<A, B>, A>, Serializable {
        static final PairFirstFunction<Object, Object> INSTANCE =
                new PairFirstFunction<Object, Object>();

        @Override
        public A apply(Pair<A, B> from) {
            return from.getFirst();
        }

        private Object readResolve() {
            return INSTANCE;
        }
    }

    /*
     * Note that this implementation doesn't involve A, and B's are only "passed
     * through", so it has become "fully variant" in both parameters.
     */
    private static final class PairSecondFunction<A, B>
            implements Function<Pair<A, B>, B>, Serializable {
        static final PairSecondFunction<Object, Object> INSTANCE =
                new PairSecondFunction<Object, Object>();

        @Override
        public B apply(Pair<A, B> from) {
            return from.getSecond();
        }

        private Object readResolve() {
            return INSTANCE;
        }
    }

    /**
     * Returns a comparator that compares two Pair objects by comparing the
     * result of {@link #getFirst()} for each.
     */
    @SuppressWarnings("unchecked") // safe contravariant cast
    public static <A extends Comparable, B> Comparator<Pair<A, B>> compareByFirst() {
        return (Comparator) FirstComparator.FIRST_COMPARATOR;
    }

    /**
     * Returns a comparator that compares two Pair objects by comparing the
     * result of {@link #getSecond()} for each.
     */
    @SuppressWarnings("unchecked") // safe contravariant cast
    public static <A, B extends Comparable> Comparator<Pair<A, B>> compareBySecond() {
        return (Comparator) SecondComparator.SECOND_COMPARATOR;
    }

    private enum FirstComparator implements Comparator<Pair<Comparable, Object>> {
        FIRST_COMPARATOR;

        @Override
        public int compare(Pair<Comparable, Object> pair1, Pair<Comparable, Object> pair2) {
            Comparable left = pair1.getFirst();
            Comparable right = pair2.getFirst();

      /*
       * Technically unsafe, but tolerable. If the comparables are badly
       * behaved, this comparator will be equally badly behaved.
       */
            @SuppressWarnings("unchecked")
            int result = left.compareTo(right);
            return result;
        }
    }

    private enum SecondComparator implements Comparator<Pair<Object, Comparable>> {
        SECOND_COMPARATOR;

        @Override
        public int compare(Pair<Object, Comparable> pair1, Pair<Object, Comparable> pair2) {
            Comparable left = pair1.getSecond();
            Comparable right = pair2.getSecond();

      /*
       * Technically unsafe, but tolerable. If the comparables are badly
       * behaved, this comparator will be equally badly behaved.
       */
            @SuppressWarnings("unchecked")
            int result = left.compareTo(right);
            return result;
        }
    }

    // TODO(kevinb): decide what level of commitment to make to this impl
    @Override
    public boolean equals(@Nullable Object object) {
        // TODO(kevinb): it is possible we want to change this to
        // if (object != null && object.getClass() == getClass()) {
        if (object instanceof Pair) {
            Pair<?, ?> that = (Pair<?, ?>) object;
            return Objects.equal(this.getFirst(), that.getFirst())
                    && Objects.equal(this.getSecond(), that.getSecond());
        }
        return false;
    }

    @Override
    public int hashCode() {
        int hash1 = first == null ? 0 : first.hashCode();
        int hash2 = second == null ? 0 : second.hashCode();
        return 31 * hash1 + hash2;
    }

    /**
     * {@inheritDoc}
     *
     * <p>This implementation returns a string in the form
     * {@code (first, second)}, where {@code first} and {@code second} are the
     * String representations of the first and second elements of this pair, as
     * given by {@link String#valueOf(Object)}. Subclasses are free to override
     * this behavior.
     */
    @Override
    public String toString() {
        // GWT doesn't support String.format().
        return "(" + getFirst() + ", " + getSecond() + ")";
    }

    private static final long serialVersionUID = 747826592375603043L;
}