package edu.byu.ece.rapidSmith.router.pathfinder;

import java.util.*;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * If the amount of data is often changing, we want to reuse old data. This is a list, that does not free its elements
 * when being cleared, but reuses them when new elements are added.
 */
public class ReusingList<E> implements Collection<E> {

    private final List<E> data = new ArrayList<>();
    private final Supplier<E> factory;
    private int usage = 0;

    public ReusingList(Supplier<E> factory) {
        this.factory = factory;
    }

    /**
     * Add a new Element to the list and return it.
     * <p>
     * Normally, a node to add would be passed to this class. However, in this way, we can reuse old nodes :)
     *
     * @return
     */
    public E addElement() {
        //Do we have an unused node in the list?
        if (usage < data.size()) {
            //Then use it
            return data.get(usage++);
        } else {
            //Allocate a new one
            E r = factory.get();
            data.add(r);
            usage++;
            return r;
        }
    }

    /**
     * Remove all elements
     */
    public void clear() {
        usage = 0;
    }

    @Override
    public Iterator<E> iterator() {
        Iterator<E> listIterator = data.iterator();
        return new Iterator<E>() {
            int current = 0;

            @Override
            public boolean hasNext() {
                return current < usage;
            }

            @Override
            public E next() {
                current++;
                return listIterator.next();
            }
        };
    }

    public int size() {
        return usage;
    }

    public boolean isEmpty() {
        return usage==0;
    }

    public boolean contains(Object o) {
        for (E e : this) {
            if (Objects.equals(e,o))
                return true;
        }
        return false;
    }

    public Object[] toArray() {
        throw new UnsupportedOperationException();
    }

    public <T> T[] toArray(T[] a) {
        throw new UnsupportedOperationException();
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append('[');
        boolean first = true;
        for (E e : this) {
            if (first)
                first = false;
            else
                sb.append(',');

            sb.append(Objects.toString(e));
        }
        sb.append(']');
        return sb.toString();
    }

    public boolean add(E e) {
        throw new UnsupportedOperationException("Use addElement instead!");
    }

    public boolean remove(Object o) {
        throw new UnsupportedOperationException();
    }

    public boolean containsAll(Collection<?> coll) {
        throw new UnsupportedOperationException();
    }

    public boolean addAll(Collection<? extends E> coll) {
        throw new UnsupportedOperationException();
    }

    public boolean removeAll(Collection<?> coll) {
        throw new UnsupportedOperationException();
    }

    public boolean retainAll(Collection<?> coll) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean removeIf(Predicate<? super E> filter) {
        throw new UnsupportedOperationException();
    }
}
